(ns vcfvis.brush
  (:use-macros [c2.util :only [p pp bind!]])
  (:require [c2.dom :as dom]
            [c2.scale :as scale]
            [c2.svg :as svg]
            [goog.fx.Dragger :as goog.fx.Dragger]
            [goog.events :as gevents]))

;;;;;;;;;;;;;;;
;;Dragger subclass that doesn't actually move its element; used just for picking up events
(defn SVGDrag [el]
  (.call goog.fx.Dragger (js* "this") el))
(goog.inherits SVGDrag goog.fx.Dragger)
(set! (.-defaultAction (.-prototype SVGDrag))
      (fn [x y] "Do nothing"))

(defn mouse-point
  "Returns SVG coordinates of a mouse event on an SVG element.
   Inspired by d3.svg.mouse."
  [$container e]
  (let [point (.createSVGPoint (or (.-ownerSVGElement $container)
                                   $container))]

    (set! (.-x point) (.-clientX e))
    (set! (.-y point) (.-clientY e))

    (let [point (.matrixTransform point (.inverse (.getScreenCTM $container)))]
      [(.-x point) (.-y point)])))

(defn resize-path-d [height direction]
  "Nice looking resize-handles; modified from Mike Bostock's Crossfilter demo page."
  (let [[x e] (case direction
                :west [-1 0]
                :east [1 1])]
    (str "M" (* 0.5 x) "," height
         "A6,6 0 0 " e " " (* 6.5 x) "," (+ height 6)
         "V" (- (* 2 height) 6)
         "A6,6 0 0 " e " " (* 0.5 x) "," (* 2 height)
         "Z"
         "M" (* 2.5 x) "," (+ height 8)
         "V" (- (* 2 height) 8)
         "M" (* 4.5 x) "," (+ height 8)
         "V" (- (* 2 height) 8))))

;;TODO generalize to support y-only brushing and full rectangular brushing.
(defn init!
  "Given scale and element to append to, creates an SVG brush overlay and returns an atom that references the brush extent."
  [el scale-x scale-y]
  (let [width (let [[xmin xmax] (:range scale-x)]
                (- xmax xmin))
        ix (scale/invert scale-x)

        height (let [[ymin ymax] (:range scale-y)]
                 (- ymax ymin))
        ;;extent of selection, in data-space
        !extent (atom [[0 0] [0 0]])
        $brush (dom/append! el [:g.brush])]

    (bind! $brush
           (let [[[x1 x2] [y1 y2]] @!extent]
             [:g.brush {:style {:visibility (when (zero? (- x1 x2)) "hidden")}}
              [:rect.background {:x 0 :width width :height height}]
              [:rect.extent {:x (scale-x x1) :width (- (scale-x x2)
                                                       (scale-x x1))
                             :height height}]
              [:g.resize.w
               [:path {:transform (svg/translate {:x (scale-x x1) :y 0})
                       :d (resize-path-d (/ height 3) :west)}]]
              [:g.resize.e
               [:path {:transform (svg/translate {:x (scale-x x2) :y 0})
                       :d (resize-path-d (/ height 3) :east)}]]]))

    ;;Mouse event handlers for creating a new selection
    (let [$background (dom/select ".background" $brush)
          !creating? (atom false)]

      (gevents/listen $background "mousedown"
                      (fn [e]
                        (reset! !creating? true)
                        (let [[x y] (mouse-point $background e)]
                          (let [x (ix x)]
                            (swap! !extent #(assoc-in % [0] [x x]))))))

      (gevents/listen $brush "mouseup"
                      (fn [_] (reset! !creating? false)))

      (gevents/listen $brush "mousemove"
                      (fn [e]
                        (when @!creating?
                          ;;then we're still within the element; update the extent
                          (let [[x y] (mouse-point $background e)]
                            (let [x (ix x)]
                              (swap! !extent (fn [[[x1 x2] ys]]
                                               [(if (> x x1) [x1 x] [x x2])
                                                ys])))))))
      (gevents/listen $brush "mouseout"
                      (fn [e]
                        (when-not (and (.-relatedTarget e)
                                       (goog.dom/contains $brush (.-relatedTarget e)))
                          (reset! !creating? false)))))



    ;;Drag event handlers for manipulating an existing selection
    (let [!extent-at-start (atom nil)
          [xmin xmax] (:domain scale-x)
          min-extent (ix 10) ;;10px min exent

          start-drag! #(reset! !extent-at-start @!extent)
          drag-fn! (fn [dragger transform-x]
                     (gevents/listen dragger goog.fx.Dragger.EventType.DRAG
                                     (fn [e]
                                       (let [[[x1 x2] ys] @!extent-at-start
                                             w (- x2 x1)
                                             ;;convert dx from pixel-space to data-space
                                             dx (- (ix (.-left e)) (ix 0))
                                             xs (transform-x dx x1 x2 w)]
                                         (reset! !extent [xs ys])))))

          dragger (SVGDrag. (dom/select ".extent" $brush))
          left (SVGDrag. (dom/select ".resize.w" $brush))
          right (SVGDrag. (dom/select ".resize.e" $brush))]

      (doseq [d [dragger left right]]
        (gevents/listen d goog.fx.Dragger.EventType.START start-drag!))

      (drag-fn! dragger (fn [dx x1 x2 w]
                          (cond
                           ;;moving too far left
                           (< (+ dx x1) xmin) [xmin (+ xmin w)]
                           ;;moving too far right
                           (> (+ dx x2) xmax) [(- xmax w) xmax]
                           :else [(+ dx x1) (+ dx x2)])))

      (drag-fn! left (fn [dx x1 x2 w]
                       (cond
                        ;;moving too far left
                        (< (+ dx x1) xmin) [xmin x2]
                        ;;moving beyond the right bar
                        (> (+ dx x1) x2) [(- x2 min-extent) x2]
                        :else [(+ dx x1) x2])))

      (drag-fn! right (fn [dx x1 x2 w]
                        (cond
                         ;;moving too far right
                         (> (+ dx x2) xmax) [x1 xmax]
                         ;;moving beyond the left bar
                         (< (+ dx x2) x1) [x1 (+ x1 min-extent)]
                         :else [x1 (+ dx x2)]))))



    ;;return extent atom.
    !extent))
