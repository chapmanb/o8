(ns vcfvis.core
  (:use-macros [c2.util :only [pp p interval]]
               [reflex.macros :only [computed-observable constrain!]]
               [dubstep.macros :only [publish! subscribe!]])
  (:require [clojure.set :as set]
            [reflex.core :as reflex]
            [dubstep.pubsub :as dubstep.pubsub]))

;;;;;;;;;;;;;;;;;;;;
;;From server

(def !context
  "Background info from server; includes descriptions and defaults for different metrics, user-specific info like available files, &c."
  (atom {}))

(def !vcfs
  "VCFs currently under analysis."
  (atom []))

(defn check-new-vcfs!
  "Retrieve new VCFs for loading, updating stored VCFs to avoid removed files."
  [new-vcfs]
  (if (seq new-vcfs)
    (do
      (reset! !vcfs (vec (filter #(contains? new-vcfs (:file-url %)) @!vcfs)))
      (set/difference new-vcfs (into #{} (map :file-url @!vcfs))))
    (reset! !vcfs [])))

;;;;;;;;;;;;;;;;;;;;
;;Derived

(def !available-files
  "User's files available on the server."
  (computed-observable
   (get @!context :files [])))

;;Y U No haz identity, clojure.set/intersection!?!?!
(defn intersection
  ([] #{})
  ([& args]
     (if (= 1 (count args))
       (first args)
       (apply set/intersection args))))

(def !shared-metrics (atom #{}))

;;;;;;;;;;;;;;;;;;;;
;;From UI

(def !metric
  "The metric to be displayed on the main histogram."
  (atom nil))

(defn select-metric! [metric]
  (reset! !metric metric))

(defn update-shared-and-selected!
  [vcfs]
  (let [shared (reduce intersection (map #(set (map :id (:available-metrics %)))
                                         vcfs))]
    (when (or (nil? @!metric)
              (not (contains? shared (:id @!metric))))
      (select-metric! (first (for [[k v] (:metrics @!context) :when (contains? shared k)] v))))
    (reset! !shared-metrics shared)))

(def !filters
  "Map of filter-id -> extent"
  (atom {}))

(def !cat-filters
  "Map of currently applied category filters: id -> #{applied filters}"
  (atom {}))

(def !visible-metrics
  "Metrics that should be shown with mini-histograms the UI"
  (atom #{}
        :validator
        (fn [ms]
          (if (contains? ms @!metric)
            (throw (js/Error. "Selected metric doesn't need a mini-histogram."))
            true))))

(constrain!
 (let [m @!metric]
   (when (contains? @!visible-metrics m)
     (swap! !visible-metrics disj m))))

(defn visible-metric? [m]
  (contains? @!visible-metrics m))

(defn toggle-visible-metric! [m]
  (swap! !visible-metrics
         (if (visible-metric? m) disj conj)
         m))

(defn update-metric! [m extent]
  (let [[min max] (m :range)
        extent (cond
                ;;clear zero extent
                (zero? (apply - extent)) nil
                ;;extend to +/- infinity if extent reaches extreme of metric range
                (= extent [min max])    [(- js/Infinity) js/Infinity]
                (= (first extent) min)  [(- js/Infinity) (second extent)]
                (= (second extent) max) [(first extent) js/Infinity]
                :else extent)]

    ;;Update the crossfilter for each VCF
    (doseq [vcf @!vcfs]
      (.filter (get-in vcf [:cf (m :id) :dimension])
               (clj->js extent)))

    ;;Save extent in metric's atom
    (swap! !filters assoc (m :id) extent)
    (reset! (m :!filter-extent) extent)

    (publish! {:filter-updated m})))

(subscribe! {:metric-brushed metric :extent extent}
            (update-metric! metric extent))
