(ns o8.xprize
  "Provide X Prize specific scoring and comparisons."
  (:use [clojure.java.io]
        [compojure.core]
        [ring.util.response]
        [shoreleave.middleware.rpc :only [defremote]]
        [bcbio.variation.config :only [get-log-status]]
        [bcbio.variation.api.shared :only [web-config]])
  (:require [cemerick.friend :as friend]
            [fs.core :as fs]
            [hiccup.core :as hiccup]
            [net.cgrand.enlive-html :as html]
            [noir.request]
            [bcbio.variation.api.run :as run]
            [bcbio.variation.web.db :as db]
            [o8.api :as api]
            [o8.pages :as pages]))

(defn- get-work-info
  [run-id]
  (-> noir.request/*request*
      :session
      :work-info
      (get run-id)))

(defremote ^{:remote-name :xprize/status} xprize-run-status
  "Retrieve details on the current status of a running scoring process."
  [run-id]
  (get-log-status {:dir {:out (-> (get-work-info run-id)
                                  :dir
                                  (fs/file "grading"))}}))

(defn- enlive->hiccup
  [el]
  (if-not (string? el)
    (->> (map enlive->hiccup (:content el))
         (concat [(:tag el) (:attrs el)])
         (keep identity)
         vec)
    el))

(defn html->hiccup
  "Convert an HTML input file into hiccup vectors.
   http://stackoverflow.com/questions/11094837/is-there-a-parser-for-html-to-hiccup-structures"
  [html-file]
  (enlive->hiccup
   (first (html/html-resource (file html-file)))))

(defremote ^{:remote-name :xprize/summary} xprize-run-summary
  "Retrieve summary HTML with results from an X Prize comparison."
  [run-id]
  (when-let [out-dir (if-let [username (api/get-username)]
                       (->> (db/get-analyses username :scoring (:db @web-config))
                            (filter #(= run-id (:analysis_id %)))
                            first
                            :location)
                       (:dir (get-work-info run-id)))]
    (let [summary-file (fs/file out-dir "scoring-summary.html")]
      (when (fs/exists? summary-file)
        (html->hiccup summary-file)))))

(defn scoring-html
  "Update main page HTML with content for scoring."
  [run-id]
  (let [score-html-file (fs/file "public" "template" "scoring.html")
        main-html-file (fs/file "public" "xprize.html")]
    (pages/base-page-w-content
     main-html-file
     [:div#main-content]
     (hiccup/html
      [:div {:id "scoring-in-process"}
       [:h3 "Status"]
       [:div {:id "scoring-status"} "Downloading input files"]
       [:div {:class "progress"}
        [:div {:id "scoring-progress"
               :class "bar" :style "width: 0%"}]]
        [:script (format "o8.xprize.score.update_run_status('%s');" run-id)]
       (slurp score-html-file)]))))

(defn- put-results-in-db
  "Run scoring analysis and store results."
  [work-info runner rclient]
  (when-let [username (:username rclient)]
    (let [comparisons @runner]
      (db/add-analysis {:username username :files (:c-files comparisons)
                        :analysis_id (:id work-info)
                        :description (format "%s: %s" (:comparison-genome work-info)
                                             (fs/base-name (-> comparisons :exp :calls second :file)))
                        :location (:dir work-info) :type :scoring}
                       (:db @web-config)))))

(defn- run-xprize-scoring
  "Run scoring, handling input parameters and updating session information."
  [{:keys [params session] :as req}]
  (let [rclient (:client (friend/current-authentication))
        ready-params  (-> params
                          (assoc :expose-fn (api/expose-dataset req)))
        {:keys [work-info runner]} (run/do-analysis :xprize ready-params rclient)
        new-work-info (assoc (:work-info session)
                        (:id work-info) work-info)]
    (future (put-results-in-db work-info runner rclient))
    (-> (response (scoring-html (:id work-info)))
        (assoc :session
          (assoc session :work-info new-work-info)))))

(defn analyses-html
  "Update main page with list of performed analyses."
  [username]
  (pages/base-page-w-content
   (fs/file "public" "xprize.html")
   [:div#main-content]
   (hiccup/html
    (if (nil? username)
      [:p "Please login to display previously run analyses."]
      [:div {:id "user-analyses" :class "container"}
       [:h3 "Previous analyses"]
       [:ul {:class "nav nav-tabs nav-stacked"}
        (map (fn [x]
               [:li
                [:a {:href "#" :id (:analysis_id x)}
                 (format "%s -- %s" (:description x)
                         (-> (java.text.SimpleDateFormat. "dd MMM yyyy HH:mm" )
                             (.format (:created x))))]])
             (db/get-analyses username :scoring (:db @web-config)))]
       [:script "o8.xprize.analyses.display_analyses()"]]))))

(defroutes xprize-routes
  (GET "/" req (pages/add-std-info "public/xprize.html"))
  (GET "/analyses" req (analyses-html (api/get-username)))
  (POST "/score" req (run-xprize-scoring req)))
