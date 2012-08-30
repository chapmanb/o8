(ns vcfvis.histogram
  (:use-macros [c2.util :only [pp p bind!]]
               [reflex.macros :only [constrain!]]
               [dubstep.macros :only [publish! subscribe!]])
  (:use [c2.core :only [unify]]
        [c2.maths :only [irange extent]])
  (:require [vcfvis.core :as core]
            [vcfvis.data :as data]
            [vcfvis.ui :as ui]
            [vcfvis.brush :as brush]
            [c2.dom :as dom]
            [singult.core :as singult]
            [c2.event :as event]
            [c2.scale :as scale]
            [c2.svg :as svg]
            [goog.string :as gstr]))

(def height ui/hist-height)
(def width ui/hist-width)
(def margin ui/hist-margin)
(def inter-hist-margin ui/inter-hist-margin)
(def axis-height ui/axis-height)

(defn hist-g* [vcf metric & {:keys [height width bars?]
                             :or {bars? true}}]
  (let [{metric-id :id scale-x :scale-x} metric
        {:keys [dimension binned bin-width]} (get-in vcf [:cf metric-id])
        ;;Since we're only interested in relative density, histograms have free y-scales.
        data-extent [0 (aget (first (.top binned 1)) "value")]
        no-data? (zero? (apply - data-extent))
        scale-y (scale/linear :domain data-extent
                              :range [0 height])
        scale-x (assoc-in scale-x [:range 1] width)
        dx (- (scale-x bin-width) (scale-x 0))]

    [:g
     [:text.message {:x (/ width 2) :y (/ height 2)}
      (when no-data?
        "No available data; try clearing filters on other dimensions.")]
     [:g.data-frame {:transform (str (svg/translate [0 height])
                                     (svg/scale [1 -1]))}
      [:g.distribution
       (when-not no-data?
         (if bars?
           (for [d (.all binned)]
             (let [x (aget d "key"), count (aget d "value")]
               [:rect.bar {:x (scale-x x)
                           :width dx
                           :height (scale-y count)}]))
           ;;else, render using a path element
           [:path
            ;; ;;Path Bars
            ;; (str "M" (scale-x x) ",0"
            ;;      "l0," h
            ;;      "l" dx "," 0
            ;;      "l" 0 "," (- h))
            {:d (str "M"
                     (.join (.map (.all binned)
                                  (fn [d]
                                    (let [x (aget d "key"), count (aget d "value")
                                          h (scale-y count)]
                                      (str (scale-x x) "," h))))
                            "L"))}]))]]]))




(defn draw-mini-hist-for-metric! [m]
  (let [vcfs @core/!vcfs
        n (count vcfs)
        mini-width (js/parseFloat (dom/style "#metrics" :width))
        mini-height 100]

    (singult/merge! (dom/select (str "#metric-" (:id m) " .mini-hist"))
                    [:div.mini-hist
                     [:svg {:width width :height (+ (* n mini-height)
                                                    (* (dec n) inter-hist-margin))}

                      (for [[vcf idx] (map vector vcfs (range))]
                        [:g {:transform (svg/translate [0 (* idx (+ mini-height inter-hist-margin))])}
                         (hist-g* vcf m
                                  :height mini-height
                                  :width mini-width
                                  :bars? false)])]])))


(defn draw-histogram! [vcfs metric]
  (let [{x :scale-x} metric
        n (count vcfs)
        hist-height (/ (- height (* n (dec inter-hist-margin))) n)]
    (singult/merge! (dom/select "#main-hist")
                    [:div#main-hist
                     [:div#histograms

                      [:div.labels
                       (for [[vcf idx] (map vector vcfs (range))]
                         [:span.label {:style {:top (str (* idx (+ hist-height inter-hist-margin)) "px")}}
                          (vcf :file-url)])]
                      
                      
                      [:svg.histogram {:width (+ width (* 2 margin)) :height (+ height (* (dec n) inter-hist-margin))}
                       [:g.hist-container {:transform (svg/translate [margin 0])}
                        (for [[vcf idx] (map vector vcfs (range))]
                          [:g {:transform (svg/translate [0 (* idx (+ hist-height inter-hist-margin))])}
                           (hist-g* vcf metric :height hist-height :width width)])]]]

                     [:div#hist-axis
                      [:div.axis.abscissa
                       [:svg {:width (+ width (* 2 margin)) :height axis-height}
                        [:g {:transform (svg/translate [margin 2])}
                         (svg/axis x (:ticks x)
                                   :orientation :bottom
                                   :formatter (partial gstr/format "%.1f"))]]]]])

    (let [!b (brush/init! "#histograms svg .hist-container"
                          x (scale/linear :range [0 height]))]

      ;;Update initial extent, if metric has it
      (reset! !b [@(metric :!filter-extent) [0 0]])

      (add-watch !b :onbrush (fn [_ _ _ [xs _]]
                               (publish! {:metric-brushed metric :extent xs}))))))

(defn clear-histogram! []
  (singult/merge! (dom/select "#main-hist")
                  [:div#main-hist
                   [:div#histograms]
                   [:div#hist-axis]])
  (publish! {:count-updated nil}))

(defn draw-mini-hists! []
  (doseq [m @core/!visible-metrics]
    (draw-mini-hist-for-metric! m)))


(constrain!
 (let [vcfs @core/!vcfs
       metric @core/!metric]
   (if (seq vcfs)
     (do
       (draw-histogram! vcfs metric)
       (draw-mini-hists!))
     ;;If no VCFs, clear everything
     (clear-histogram!))))

