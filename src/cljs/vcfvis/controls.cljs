(ns vcfvis.controls
  (:use-macros [c2.util :only [pp p bind!]]
               [reflex.macros :only [constrain!]]
               [dubstep.macros :only [publish! subscribe!]])
  (:use [chosen.core :only [ichooseu! options]]
        [c2.core :only [unify]]
        [c2.util :only [clj->js]])
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [vcfvis.core :as core]
            [vcfvis.histogram :as histogram]
            [vcfvis.data :as data]
            [c2.dom :as dom]
            [c2.event :as event]
            [singult.core :as singult]
            [shoreleave.remotes.http-rpc :as rpc]
            [goog.string :as gstring]))

;;;;;;;;;;;;;;;;;;
;;File multiselect
(def file-selector
  (let [$selector (dom/append! "#file-selector"
                               [:select {:multiple "multiple"
                                         :data-placeholder "Select VCF files"}])
        !c (ichooseu! $selector :search-contains true)]

    ;;The selector should always reflect the user's avaliable flies
    (constrain!
     (options !c (map (fn [{:keys [filename folder id]}]
                        {:text filename :value id :group folder})
                      @core/!available-files)))
    !c))

;;;;;;;;;;;;;;;;;;;;;;;
;;Metrics mini-hists
(bind! "#metrics"
       (let [shared @core/!shared-metrics
             selected-metric (:id @core/!metric)
             metrics (for [m (vals (@core/!context :metrics))]
                       (assoc m
                         :selected? (= (:id m) selected-metric)
                         :visible? (core/visible-metric? m)
                         :shared? (contains? shared (m :id))))]
         [:div#metrics
          (unify metrics
                 (fn [{:keys [id desc selected? visible? shared?]}]
                   [:div.metric {:id (str "metric-" id)
                                 :class (str (when selected? "selected")
                                             " " (when visible? "expanded")
                                             " " (when-not shared?  "disabled"))}
                    [:h2 id]
                    [:button.expand-btn "V"]
                    [:span.desc desc]
                    [:div.mini-hist (singult/ignore)]
                    [:div.sort-handle]])
                 :key-fn #(:id %))]))

(-> (js/jQuery "#metrics")
    (.sortable (js-obj "handle" ".sort-handle")))

(event/on "#metrics" :click
          (fn [d _ e]
            (when-not (dom/matches-selector? (.-target e) ".expand-btn")
              (core/select-metric! (dissoc d :selected? :shared? :visible?)))))

(event/on "#metrics" ".expand-btn" :click
          (fn [d]
            (let [m (dissoc d :selected? :shared? :visible?)]
              (when-not (core/visible-metric? m)
                ;;then it's about to become visible; draw the mini-hist
                (histogram/draw-mini-hist-for-metric! m))
              (core/toggle-visible-metric! m))))

(subscribe! {:filter-updated m}
            (when (= :category (get-in m [:x-scale :type]))
              (histogram/draw-histogram! @core/!vcfs @core/!metric))
            (histogram/draw-mini-hists!)
            (publish! {:count-updated (.value (get-in (first @core/!vcfs) [:cf :all]))}))

(subscribe! {:count-updated x}
            (singult/merge! (dom/select "#filter-summary")
                            [:table.table.table-condensed#filter-summary
                             [:tbody
                              (concat
                               [[:tr
                                 [:td [:span#count-pad]]
                                 [:td [:span#count (if x (str x " variants") "")]]]]
                               (for [[m-id extent] @core/!filters :when extent]
                                 [:tr
                                  [:td m-id]
                                  [:td (format "%.1f-%.1f" (first extent) (second extent))]])
                               (for [[cat-id vals] @core/!cat-filters :when (seq vals)]
                                 [:tr
                                  [:td cat-id]
                                  [:td (string/join ", " vals)]]))]]))

(defn- combine-categories
  "Combine multiple sets of categories, including all choices"
  [vcfs]
  (let [shared (reduce core/intersection (map #(set (map :id (:available-categories %)))
                                              vcfs))
        cats (map :available-categories vcfs)]
    (->> (reduce (fn [coll x]
                   (if-let [cur (get coll (:id x))]
                     (assoc coll (:id x)
                            (assoc cur :choices (set/union (:choices cur) (:choices x))))
                     (assoc coll (:id x) x)))
                 (into {} (for [x (first cats) :when (contains? shared (:id x))]
                            [(:id x) x]))
                 (apply concat (rest cats)))
         vals
         (sort-by :id))))

;; ## Filters
(bind! "#cat-filters"
       (let [cs (combine-categories @core/!vcfs)]
         [:div#cat-filters
          (unify cs
                 (fn [{:keys [id desc choices]}]
                   [:div.filter.metric {:id (str "filter-" id)}
                    [:span.desc desc]
                    (for [group-choices (partition-all 3 (sort choices))]
                      [:div.btn-group {:data-toggle "buttons-checkbox"}
                       (for [x group-choices]
                         [:btn.btn.filter-btn x])])]))]))

(defn update-category-filter!
  [cat val off?]
  (let [shared (into {} (for [x (combine-categories @core/!vcfs)]
                          [(:id x) (:choices x)]))
        orig (get @core/!cat-filters (:id cat) #{})
        new (if off? (disj orig val) (conj orig val))]
    (when (contains? (get shared (:id cat)) val)
      (swap! core/!cat-filters assoc (:id cat) new)
      (doseq [vcf @core/!vcfs]
        (.filter (get-in vcf [:cf (:id cat) :dimension])
                 (fn [d]
                   (or (empty? new)
                       (not (empty? (core/intersection (set d) new)))))))
      (publish! {:filter-updated cat}))))

(event/on "#cat-filters" :click
          (fn [d _ e]
            (update-category-filter! d (dom/text (.-target e))
                                     (gstring/contains (dom/attr (.-target e) :class) "active"))))

;;;;;;;;;;;;;;;;;;;;;;;
;; Analysis buttons
(let [$btn (dom/select "#filter-btn")]
  (bind! $btn
         (if (or (zero? (count @core/!vcfs))
                 (zero? (count (merge @core/!filters @core/!cat-filters))))
           [:button#filter-btn.btn {:properties {:disabled true}} "Filter subset"]
           (case (get @data/!analysis-status
                      (get (first @core/!vcfs) :file-url))
             :completed [:button#filter-btn.btn {:properties {:disabled true}} "Completed"]
             :running   [:button#filter-btn.btn {:properties {:disabled true}} "Running..."]
             nil        [:button#filter-btn.btn {:properties {:disabled false}} "Filter subset"])))

  (event/on-raw $btn :click
                (fn [_]
                  (data/filter-analysis (get (first @core/!vcfs) :file-url)
                                        (merge @core/!filters @core/!cat-filters)))))

(let [$btn (dom/select "#clinvar-btn")]
  (bind! $btn
         (if (pos? (count @core/!vcfs))
           [:button#clinvar-btn.btn {:properties {:disabled false}} "Display at ClinVar"]
           [:button#clinvar-btn.btn {:properties {:disabled true}} "Display at ClinVar"]))
  (event/on-raw $btn :click
                (fn [_]
                  (let [file-url (:file-url (first @core/!vcfs))
                        metrics (merge @core/!filters @core/!cat-filters)]
                    (rpc/remote-callback "run/clinvar" [file-url metrics]
                                         (fn [clinvar-url]
                                           (pp clinvar-url)))))))
