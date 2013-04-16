(ns vcfvis.main
  (:use-macros [c2.util :only [pp p timeout]]
               [reflex.macros :only [constrain!]])
  (:require [vcfvis.core :as core]
            [vcfvis.data :as data]
            [vcfvis.ui :as ui]
            ;;[vcfvis.stub :as data]
            [vcfvis.controls :as controls]))

(add-watch controls/file-selector :load-metrics
           (fn [files]
             (let [new-files (core/check-new-vcfs! files)]
               (when (seq new-files)
                 (.modal (js/jQuery "#waiting-modal") "show")
                 (doseq [f new-files]
                   (data/load-vcf f (fn [vcf]
                                      (.modal (js/jQuery "#waiting-modal") "hide")
                                      (core/update-shared-and-selected! (conj @core/!vcfs vcf))
                                      (swap! core/!vcfs conj vcf))))))))

;;Request file list from server
(.modal (js/jQuery "#waiting-modal")
        (clj->js {:backdrop "static" :keyboard false :show true}))
(data/load-context (fn [context]
                     (ui/set-user)
                     (ui/set-navigation)
                     (.modal (js/jQuery "#waiting-modal") "hide")
                     (reset! core/!context context)))
