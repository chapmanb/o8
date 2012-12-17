;;Interactive functionality for X prize scoring based web pages.

(ns o8.xprize.score
  (:use-macros [c2.util :only [pp]])
  (:require [clojure.string :as string]
            [c2.dom :as dom]
            [chosen.core :as chosen]
            [shoreleave.remotes.http-rpc :as rpc]
            [goog.string :as gstring]
            [goog.Timer :as timer])
  (:require-macros [shoreleave.remotes.macros :as sl]))

;; ## Display scoring results

(defn- progress-percent
  "Rough progress points to indicate status of processing."
  [desc]
  (cond
   (gstring/startsWith desc "Starting variation") 10
   (gstring/startsWith desc "Prepare VCF, resorting to genome build: contestant") 15
   (gstring/startsWith desc "Normalize MNP and indel variants: contestant") 60
   (gstring/startsWith desc "Comparing VCFs: reference vs contestant") 75
   (gstring/startsWith desc "Summarize comparisons") 90
   (gstring/startsWith desc "Finished") 100
   :else nil))

(defn ^:export update-run-status
  "Update summary page with details about running statuses."
  [run-id]
  (sl/rpc ("xprize/status" run-id) [info]
          (if (= :finished (:state info))
            (sl/rpc ("xprize/summary" run-id) [sum-html]
                    (if (nil? sum-html)
                      (timer/callOnce (fn [] (update-run-status run-id)) 2000)
                      (dom/replace! "#scoring-in-process" sum-html)))
            (do
              (when-not (nil? info)
                (dom/text "#scoring-status" (:desc info))
                (when-let [pct (progress-percent (:desc info))]
                  (dom/style "#scoring-progress" :width (str pct "%"))))
              (timer/callOnce (fn [] (update-run-status run-id)) 2000)))))

;; ## Retrieve remote file information

(defn- remote->chosen [xs]
  (map (fn [x] {:value (:full x) :text (:name x)}) xs))

(defn- update-remote-files!
  "Update remote file information based on parent folder"
  [file-chosen dir ftype]
  (sl/rpc ("variant/external-files" dir ftype) [files]
          (chosen/options file-chosen (remote->chosen files))))

(defn prep-remote-selectors
  "Prepare dropdowns for retrieval via GenomeSpace or Galaxy."
  [select-id ftype]
  (let [folder-chosen (chosen/ichooseu! (str "#" select-id "-folder"))
        file-chosen (chosen/ichooseu! (str "#" select-id "-file"))]
    (sl/rpc ("variant/external-dirs") [dirs]
            (chosen/options folder-chosen (remote->chosen dirs))
            (when-let [cur-dir (chosen/selected folder-chosen)]
              (update-remote-files! file-chosen cur-dir ftype))
            (add-watch folder-chosen :change
                       (fn [dir]
                         (update-remote-files! file-chosen dir ftype))))))

(defn- prep-genome-selector
  "Prepare genome selector to pick analysis genome."
  []
  (let [genome-chosen (chosen/ichooseu! "#comparison-genome")]
    (sl/rpc ("meta/genomes") [genomes]
            (chosen/options genome-chosen genomes))))

(defn ^:export setup-remotes 
  "Setup retrieval of file information from GenomeSpace and Galaxy."
  []
  (prep-genome-selector)
  (prep-remote-selectors "variant" "vcf")
  (prep-remote-selectors "region" "bed"))
