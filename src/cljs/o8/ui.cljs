;; Generally useful user interface functionality
(ns o8.ui
  (:use-macros [c2.util :only [pp]])
  (:require [clojure.string :as string]
            [c2.dom :as dom]
            [c2.event :as event]
            [shoreleave.remotes.http-rpc :as rpc]))

(defn ^:export set-navigation
  "Correctly set the active top level navigation toolbar."
  []
  (let [loc (-> (.toString window.location ())
                (string/split #"/" 4)
                last)]
    (doseq [list-item (dom/children (dom/select "#top-navbar"))]
      (if (= (str "/" loc)
             (-> (dom/children list-item)
                 first
                 (dom/attr :href)))
        (do
          (dom/add-class! list-item "active")
          (dom/add-class! list-item "disabled")
          (-> (dom/children list-item)
              first
              (dom/attr :href nil)))
        (do
          (dom/remove-class! list-item "active")
          (dom/remove-class! list-item "disabled"))))))

(defn- logged-in-dropdown [user]
  [:ul {:class "nav pull-right" :id "user-dropdown"}
   [:li {:class "divider-vertical"}]
   [:li {:class "dropdown"}
    [:a {:href "#" :class "dropdown-toggle" :data-toggle "dropdown"}
     [:i {:class "icon-user icon-white" :style "margin-right: 6px"}]
     user
     [:span {:class "caret" :style "margin-left: 6px"}]]
    [:ul {:class "dropdown-menu"}
     [:li [:a {:id "logout-btn" :href "/logout"} "Logout"]]]]])

(defn ^:expose set-user
  "Set user information with dropdown in top level navigation toolbar."
  []
  (rpc/remote-callback "meta/username" []
                       (fn [username]
                         (when username
                           (dom/replace! "#user-dropdown"(logged-in-dropdown username))))))
