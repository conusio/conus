(ns conus.routes.home
  (:require [conus.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [conus.db.core :as db]
            [struct.core :as st])
  (:import [java.io File FileInputStream FileOutputStream]))

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

(def resource-path "resources/public/images/")

(defn file-path [path & [filename]]
  (java.net.URLDecoder/decode
   (str path File/separator filename)
   "utf-8"))

(defn upload-file
  "uploads a file to the target folder
   when :create-path? flag is set to true then the target path will be created"
  [path {:keys [tempfile size filename]}]
  (let [_ (log/info "tempfile: " tempfile
                    " \nfileoutputstream: "(file-path path filename))])
  (try
    (with-open [in (new FileInputStream tempfile)
                out (new FileOutputStream (file-path path filename))]
      (let [source (.getChannel in)
            dest   (.getChannel out)]
        (.transferFrom dest source 0 (.size source))
        (.flush out)))))

(defn validate-message [params]
  (first
    (st/validate params message-schema)))

(defn save-message! [{:keys [params] :as whole-thing}]
  (let [_ (log/info "the whole-thing is" whole-thing)
        _ (upload-file resource-path (:file params))
        params-with-file-name (assoc params :imageurl (str "/images/" (get-in params [:file :filename])))]
    (if-let [errors (validate-message params)]
      (-> (response/found "/")
          (assoc :flash (assoc params :errors errors)))
      (do
        (db/save-message!
         (assoc params-with-file-name :timestamp (java.util.Date.)))
        (response/found "/")))))

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
  (GET "/about" [] (about-page))
  (GET "/upload" []
       (layout/render "upload.html"))
  (POST "/upload" [file]
        (upload-file resource-path file)
        (redirect (str "/anything/" (:filename file))))
  (GET "/anything/:filename" [filename]
       (let [_  (log/info "file-response: " (file-response (str resource-path filename)))])
       (file-response (str resource-path filename))))
