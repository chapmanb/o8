(ns vcfvis.data
  (:use-macros [c2.util :only [pp p]]
               [reflex.macros :only [constrain!]]
               [dubstep.macros :only [subscribe!]])
  (:use [cljs.reader :only [read-string]])
  (:require [goog.Timer :as timer]
            [vcfvis.core :as core]
            [vcfvis.ui :as ui]
            [shoreleave.remotes.http-rpc :as rpc]
            [c2.scale :as scale]
            [c2.ticks :as ticks]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;Processing data retrieved from server

(defn expand-metric
  "Adds bin-width, filter extent atom, and x-scale with tick marks to a metric."
  [metric]
  (if (metric :range)
    (assoc metric
      :bin-width (let [[start end] (metric :range)]
                   (/ (- end start) ui/hist-bins))
      :!filter-extent (atom nil)
      :scale-x (let [{:keys [ticks]} (ticks/search (metric :range)
                                                   :clamp? true :length ui/hist-width)
                     x (scale/linear :domain (metric :range)
                                     :range [0 ui/hist-width])]
                 (assoc x :ticks ticks)))
    metric))

(defn- add-metric-w-xscale
  "Add metrics with the specified xscale axis type."
  [xscale-type]
  (fn [res m]
    (if (= (get-in m [:x-scale :type] :linear) xscale-type) 
      (assoc res (:id m)
             (expand-metric m))
      res)))

(defn prep-context [context]
  (-> context
      (update-in [:metrics]
                 #(reduce (add-metric-w-xscale :linear)
                          {} %))
      (assoc :categories
        (reduce (add-metric-w-xscale :category)
                {} (:metrics context)))))

(defn- collect-cur-metrics
  "Expand metrics to full maps available in the context."
  [core-kw orig-metrics]
  (let [core-metrics (get @core/!context core-kw)]
    (reduce (fn [ms m]
              (if (contains? core-metrics (:id m))
                (conj ms (expand-metric m))
                ms))
            #{} orig-metrics)))

(defn prep-vcf-json [vcf-json]
  (let [input (read-string (aget vcf-json "clj"))
        info (-> input
                 (update-in [:available-metrics] (partial collect-cur-metrics :metrics))
                 (assoc :available-categories
                   (collect-cur-metrics :categories (:available-metrics input))))
        cf (js/crossfilter (aget vcf-json "raw"))]

  (assoc info
    :cf (into {:crossfilter cf
               :all (.groupAll cf)}
              (concat
               (for [{:keys [id range bin-width]} (info :available-metrics)]
                 (let [[start end] range
                       dim (.dimension cf #(aget % id))
                       binned (.group dim (fn [x]
                                            (+ start (* bin-width
                                                        ;;take the min to catch any roundoff into the last bin
                                                        (min (Math/floor (/ (- x start) bin-width))
                                                             (dec ui/hist-bins))))))]
                   [id {:bin-width bin-width
                        :dimension dim
                        :binned binned}]))
               (for [{:keys [id]} (info :available-categories)]
                 [id {:dimension (.dimension cf #(aget % id))}]))))))

;;;;;;;;;;;;;;;
;;Fetching data

(defn load-context [callback]
  (rpc/remote-callback "variant/context" []
                       (fn [res]
                         (callback (prep-context res)))))

(defn load-vcf [file-url callback]
  (.getJSON js/jQuery "/api/vcf"
            (clj->js {:file-url file-url})
            (fn [res]
              (callback (prep-vcf-json res)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;Exporting filter selection

(def !analysis-status
  "File analysis status---are analyses running, completed, &c.?
   Keyed by filename."
  (atom {}))

;;Whenever filter changes we're looking at a new subset of the data, so reset the filter button.
(subscribe! {:filter-updated _} (reset! !analysis-status {}))

(defn update-status! [filename status]
  (swap! !analysis-status assoc-in [filename] status))

(defn reset-statuses! []
  (reset! !analysis-status {}))

(defn filter-analysis [file-url metrics]
  (update-status! file-url :running)
  (rpc/remote-callback "run/filter" [file-url metrics]
                       (fn [res]
                         (update-status! file-url :completed))))

;; ## Export to ClinVar

(def !clinvar-status
  "Status of a ClinVar submission keyed to filename."
  (atom {}))

(defn check-clinvar-submission
  "Callback to monitor submission to ClinVar, making remote URL available when ready."
  [file-url clinvar-id]
  (rpc/remote-callback "status/clinvar" [clinvar-id]
                       (fn [clinvar-url]
                         (if (nil? clinvar-url)
                           (timer/callOnce (fn [] (check-clinvar-submission file-url clinvar-id))
                                           2000)
                           (swap! !clinvar-status assoc file-url {:status :ready
                                                                  :url clinvar-url})))))

(defn submit-to-clinvar
  "Submit the current file, post-filtering, to ClinVar."
  [file-url metrics]
  (swap! !clinvar-status assoc file-url {:status :send})
  (rpc/remote-callback "run/clinvar" [file-url metrics]
                       (fn [clinvar-id]
                         (check-clinvar-submission file-url clinvar-id))))
