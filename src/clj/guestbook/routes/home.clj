(ns guestbook.routes.home
  (:require [guestbook.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [guestbook.db.core :as db]
            [struct.core :as st]))

(defn one
  []
  1)

(defn home-page [{:keys [flash]}]
  (layout/render
    "home.html"
    (merge {:messages (db/get-messages)}
           (select-keys flash [:name :message :errors]))))

(def message-schema
  [[:name st/required st/string]
   [:message
    st/required
    st/string
    {:message "message must contain at least 10 characters"
     :validate #(> (count %) 9)}]])

(defn validate-message [params]
  (first
    (st/validate params message-schema)))

(defn save-message! [{:keys [params]}]
  (if-let [errors (validate-message params)]
    (-> (response/found "/")
        (assoc :flash (assoc params :errors errors)))
    (do
      (db/save-message!
        (assoc params :timestamp (java.util.Date.)))
      (response/found "/"))))

(defn about-page []
  (layout/render "about.html"))

(defn user-list [poo]
  (let [_ (log/info "get-messages:" {:messages (distinct (map #(:name %) (db/get-messages)))})])
  (layout/render "user.html"
                 {:messages (distinct (map #(:name %) (db/get-messages)))}
                 #_(merge {:messages (db/get-messages)}
                        (select-keys flash [:name]))))

(defroutes home-routes
  (GET "/" request (home-page request))
  (POST "/" request (save-message! request))
  (GET "/user" request (user-list request))
  (GET "/about" [] (about-page)))
