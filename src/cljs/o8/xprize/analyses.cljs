;; Interactive functionality for display of older analyses.

(ns o8.xprize.analyses
  (:use-macros [c2.util :only [pp]])
  (:require [c2.dom :as dom]
            [c2.event :as event]
            [shoreleave.remotes.http-rpc :as rpc])
  (:require-macros [shoreleave.remotes.macros :as sl]))

(defn- display-selected-analysis
  "Update page with details from a selected analysis."
  [analysis-id]
  (sl/rpc ("xprize/summary" analysis-id) [sum-html]
          (dom/replace! "#user-analyses" sum-html)))

(defn ^:export display-analyses
  "Correctly set the top level navigation toolbar."
  []
  (doseq [x (-> (dom/select "#user-analyses ul")
                dom/children)]
    (event/on-raw x
                  :click (fn [evt]
                           (display-selected-analysis (dom/attr (.-target evt) :id)))
                  :capture true)))
