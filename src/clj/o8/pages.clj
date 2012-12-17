(ns o8.pages
  "Processing and preparation of display and input pages."
  (:use [clojure.java.io]
        [bcbio.variation.api.shared :only [web-config]])
  (:require [hiccup.core :as hiccup]
            [net.cgrand.enlive-html :as html]
            [ring.middleware.anti-forgery :as anti-forgery]))

(defn base-page-w-content
  "Update input HTML with specified content at selector"
  [input-html selector new-hiccup-html]
  (-> (html/xml-resource (file input-html))
      (html/transform selector (-> new-hiccup-html
                                   java.io.StringReader.
                                   html/html-resource
                                   html/content))
      html/emit*
      (#(apply str %))))

(defn add-anti-forgery
  "Return main HTML page with submit information."
  [page]
  (base-page-w-content page [:div#anti-forgery]
                       (hiccup/html [:input {:class "hidden"
                                             :name "__anti-forgery-token"
                                             :value anti-forgery/*anti-forgery-token*}])))

(defn- google-analytics
  [account-id]
  ["var _gaq = _gaq || [];"
   (format "_gaq.push(['_setAccount', '%s']);" account-id)
   "_gaq.push(['_trackPageview']);"
   "(function() {"
   "var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;"
   "ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';"
   "var s = document.getElementsByTagName('script')[0] ; s.parentNode.insertBefore(ga, s);"
   "})();"])

(defn add-analytics
  [base-str]
  (if-let [account-id (get-in @web-config [:params :web :google-analytics])]
    (-> (html/xml-resource (java.io.StringReader. base-str))
        (html/transform [:script#google-analytics]
                        (html/content (google-analytics account-id)))
        html/emit*
        (#(apply str %)))
    base-str))

(defn- is-xprize? []
  (get-in @web-config [:params :web :xprize]))

(defn filter-xprize-links
  [base-str]
  (if (is-xprize?)
    base-str
    (-> (html/xml-resource (java.io.StringReader. base-str))
        (html/transform [:li.xprize]
                        (html/add-class "hidden"))
        html/emit*
        (#(apply str %)))))

(defn add-footer
  [base-str]
  (let [always-present [["http://www.edgebio.com/" "EdgeBio"]
                        ["http://compbio.sph.harvard.edu/chb/" "Harvard School of Public Health"]]
        final (if (is-xprize?)
                (conj
                 (vec (cons ["http://genomics.xprize.org/"
                             "Archon Genomics XPrize presented by Express Scripts"]
                            always-present))
                 ["http://www.ncsa.illinois.edu/" "NCSA"])
                (conj always-present
                      ["http://keminglabs.com/" "Keming Labs"]))
        footer (-> [:p (map-indexed (fn [i [url name]]
                                      [:span (when (pos? i) " | ")
                                       [:a {:href url :target "_blank"} name]]) final)]
                   hiccup/html
                   java.io.StringReader.
                   html/html-resource)]
    (-> (html/xml-resource (java.io.StringReader. base-str))
        (html/transform [:footer.footer]
                        (html/content footer))
        html/emit*
        (#(apply str %)))))

(defn add-welcome
  "Add welcome template to login page, either for X Prize or visualization."
  [base-str]
  (let [welcome-template (if (is-xprize?)
                           (file "public" "template" "welcome-xprize.html")
                           (file "public" "template" "welcome-viz.html"))]
    (-> (html/xml-resource (java.io.StringReader. base-str))
        (html/transform [:div#welcome]
                        (html/content (html/html-resource (file welcome-template))))
        html/emit*
        (#(apply str %)))))

(defn add-std-info
  "Add standard additions to the input HTML page:
   - anti-forgery tokens
   - google analytics callbacks
   - filter X-Prize specific links
   - standard footer
   - Provide welcome page for login."
  [page]
  (-> page
      add-anti-forgery
      add-analytics
      add-footer
      add-welcome
      filter-xprize-links))