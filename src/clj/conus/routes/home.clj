(ns conus.routes.home
  (:require [conus.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [ring.util.response :refer [redirect file-response]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [conus.db.core :as db]
            [struct.core :as st]
            [conus.middleware :as mid]
            [cemerick.friend :as friend]
            [taoensso.timbre :as timbre])
  (:import [java.io File FileInputStream FileOutputStream]))

;; https://github.com/conusio/conus/issues/7
(defn fix-url-commas [items]
  (for [item items]
    (assoc item :url-name (clojure.string/replace (:name item) #"," "%2c"))))

(defn home-page [{:keys [flash]}]
  (layout/render
    "home.html"
    (merge {:messages (fix-url-commas (db/get-messages))}
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

(defn upload-file!
  "uploads a file to the target folder
   when :create-path? flag is set to true then the target path will be created"
  [path {:keys [tempfile size filename]} random-prefix]
  (let [
        _ (log/info "tempfile: " tempfile
                    " \nfileoutputstream: "(file-path path (str random-prefix  filename)))])
  (try
    (with-open [in (new FileInputStream tempfile)
                out (new FileOutputStream (file-path path (str random-prefix filename)))]
      (let [source (.getChannel in)
            dest   (.getChannel out)]
        (.transferFrom dest source 0 (.size source))
        (.flush out)))))

(defn validate-message [params]
  (first
    (st/validate params message-schema)))

(defn fix-params [params random-prefix]
  (as-> params $
    (assoc $ :imageurl (str "/images/" (str random-prefix (get-in params [:file :filename]))))
    (assoc $ :name (clojure.string/trim (:name $)))))

(defn upload-file-helper! [params random-prefix]
  (when (not= "" (get-in params [:file :filename])) (upload-file! resource-path (:file params) random-prefix)))

(defn save-message! [{:keys [params] :as request}]
  (let [random-prefix (str (rand-int 1000000) "-conus-")
        _ (log/info "the http request is" request)
        _ (upload-file-helper! params random-prefix)
        fixed-params (fix-params params random-prefix)]
    (if-let [errors (validate-message fixed-params)]
      (-> (response/found "/")
          (assoc :flash (assoc fixed-params :errors errors)))
      (do
        (db/save-message!
         (assoc fixed-params :timestamp (java.util.Date.)))
        (response/found "/")))))

(defn about-page []
  (layout/render "about.html"))

(defn user-list []
  (let [_ (log/info "get-messages:" {:messages (distinct (map #(:email %) (db/get-messages)))})])
  (layout/render "user.html"
                 {:messages (distinct (map #(:email %) (db/get-messages)))}))

(defn user-page [user]
  (let [_ (log/info {:messages (filter #(= (str user) (:email %)) (db/get-messages))})])
  (layout/render "user-page.html"
                 {:messages (filter #(= (str user) (:email %)) (fix-url-commas (db/get-messages))) :user user :email user}))


(defn user-product-page [user user-product]
  (let [_ (log/info {:messages (filter #(= (str user) (:email %)) (db/get-messages))})])
  (layout/render "user-product-page.html"
                 {:messages (filter #(and
                                      (= (str user) (:email %))
                                      (= (str user-product) (:name %)))
                                    (db/get-messages)) :user user :name user-product}))

(defroutes home-routes
  ;; you can view the home page, and view and share links to products without being logged in.
  (GET "/user/:user/:user-product" [user user-product] (user-product-page user user-product))
  (GET "/" request (home-page request))

  ;; for anything else, you need to be logged in.
  (POST "/" request (friend/authorize #{:conus.middleware/user} (save-message! request)))
  (GET "/user" request (friend/authorize #{:conus.middleware/user} (user-list)))
  (GET "/user/:user" [user]  (friend/authorize #{:conus.middleware/user} (user-page user)))
  (POST "/upload" [file]
        (friend/authorize #{:conus.middleware/user}  (upload-file! resource-path file))
        (friend/authorize #{:conus.middleware/user} (redirect (str "/anything/" (:filename file)))))
  (GET "/anything/:filename" [filename]
       (let [_  (log/info "file-response: " (file-response (str resource-path filename)))])
       (friend/authorize #{:conus.middleware/user} (file-response (str resource-path filename))))
  ;; debugging
  (GET "/show-info" request #_(conus.middleware/render-users-info request)
       (friend/authorize #{:conus.middleware/user} (conus.middleware/render-users-info request))))
