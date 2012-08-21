(ns vcfvis.controls
  (:use-macros [c2.util :only [pp p bind!]]
               [reflex.macros :only [constrain!]]
               [dubstep.macros :only [publish! subscribe!]])
  (:use [chosen.core :only [ichooseu! options]]
        [c2.core :only [unify]]
        [c2.util :only [clj->js]])
  (:require [vcfvis.core :as core]
            [c2.dom :as dom]
            [c2.event :as event]))


;;;;;;;;;;;;;;;;;;
;;File multiselect
(def file-selector
  (let [$selector (dom/append! "#file-selector"
                               [:select {:multiple "multiple"
                                         :data-placeholder "Select VCF files"}])
        !c (ichooseu! $selector)]

    ;;The selector should always reflect the user's avaliable flies
    (constrain!
     (options !c (map (fn [{:keys [filename id]}]
                        {:text filename :value id})
                      @core/!available-files)))
    !c))




;;;;;;;;;;;;;;;;;;;;;;;
;;Metrics mini-hists
(bind! "#metrics"
       (let [shared (set (map :id @core/!shared-metrics))
             selected-metric @core/!metric
             metrics (for [m (vals (@core/!context :metrics))]
                       (assoc m
                         :selected? (= m selected-metric)
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
                    [:div.mini-hist
                     ;;TODO implement "ignore these children" semantics in Singult.
                     [:svg [:g [:g [:path]]]]]])
                 :key-fn #(:id %))]))

(event/on "#metrics" :click
          (fn [d _ e]
            (when-not (dom/matches-selector? (.-target e) ".expand-btn")
              (core/select-metric! (dissoc d :selected? :shared? :visible?)))))

(event/on "#metrics" ".expand-btn" :click
          (fn [d] (core/toggle-visible-metric! (dissoc d :selected? :shared? :visible?))))


;;;;;;;;;;;;;;;;;;;;;;;
;;TODO: download button

;; (case (get @data/!analysis-status (vcf :filename))
;;                  :completed  [:button.btn {:properties {:disabled true}} "Completed"]
;;                  :running    [:button.btn {:properties {:disabled true}} "Running..."]
;;                  nil         [:button.btn {:properties {:disabled false}} "Export subset"])


(let [$btn (dom/append! "body" [:button "Download subset"])]
  (event/on-raw $btn :click
                (fn [e]
                  (pp (core/current-filters)))))
