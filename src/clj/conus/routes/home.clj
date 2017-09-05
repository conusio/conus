(ns conus.routes.home
  (:require [conus.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [conus.db.core :as db]
            [struct.core :as st]))

(defn home-page [{:keys [flash]}]
  (layout/render
    "home.html"
    (merge {:messages (db/get-messages)}
           (select-keys flash [:name :message :errors]))))

(def message-schema
  [#_[:name st/required st/string]
   #_[:message
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
  (let [_ (log/info "get-messages:" {:messages (distinct (map #(:email %) (db/get-messages)))})])
  (layout/render "user.html"
                 {:messages (distinct (map #(:email %) (db/get-messages)))}))

(defn user-page [user]
  (let [_ (log/info {:messages (filter #(= (str user) (:email %)) (db/get-messages))})])
  (layout/render "user-page.html"
                 {:messages (filter #(= (str user) (:email %)) (db/get-messages)) :user user}))


(defn user-product-page [user user-product]
  (let [_ (log/info {:messages (filter #(= (str user) (:email %)) (db/get-messages))})])
  (layout/render "user-product-page.html"
                 {:messages (filter #(and
                                      (= (str user) (:email %))
                                      (= (str user-product) (:name %)))
                                    (db/get-messages)) :user user :name user-product}))

(defroutes home-routes
  (GET "/" request (home-page request))
  (POST "/" request (save-message! request))
  (GET "/user" request (user-list request))
  (GET "/user/:user" [user] (user-page user))
  (GET "/user/:user/:user-product" [user user-product] (user-product-page user user-product))
  (GET "/about" [] (about-page)))