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
            [cemerick.url :as url]
            [cemerick.friend :as friend]
            [conus.config :refer [env]]
            [taoensso.timbre :as timbre])
  (:import [java.io File FileInputStream FileOutputStream]))

(defn encode-urls [items]
  (for [item items]
    (assoc item :url-name (url/url-encode (:name item)))))

(defn home-page [{:keys [flash]}]
  (let [_ (log/info (db/get-for-home-page))])
  (layout/render
    "home.html"
    (merge {:messages (encode-urls (reverse (take-last 40 (sort-by :timestamp (db/get-for-home-page)))))}
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
  [path {:keys [tempfile size filename]} random-prefix]
  (io/copy tempfile (io/file (str path random-prefix filename))))

(defn validate-message [params]
  (first
    (st/validate params message-schema)))

(defn fix-params [params random-prefix]
  (as-> params $
    (assoc $ :imageurl (if (not (empty? (get-in params [:file :filename])))
                         (str "/images/" random-prefix (get-in params [:file :filename]))))
    (assoc $ :name (if (not (empty? (clojure.string/trim (:name $))))
                     (clojure.string/trim (:name $))
                     (str random-prefix "untitled")))))

(defn upload-file-helper! [params random-prefix]
  (when (not= "" (get-in params [:file :filename])) (upload-file! resource-path (:file params) random-prefix)))

(defn get-owner [request]
  (if (:ignore-http env)
    1
    (let [user (mid/get-user-info (mid/get-token request))]
      (:id (db/get-owner-from-login {:login (:login user)}))))) ;; this is also messy

(defn save-message! [{:keys [params] :as request}]
  (let [random-prefix (str (rand-int 1000000) "-conus-")
        _ (upload-file-helper! params random-prefix)
        fixed-params (fix-params params random-prefix)]
    (if-let [errors (validate-message fixed-params)]
      (-> (response/found "/")
          (assoc :flash (assoc fixed-params :errors errors)))
      (do
        (db/save-message!
         (assoc fixed-params :timestamp (java.util.Date.) :email (get-owner request)))
        (db/save-thing!
         (assoc fixed-params :owner (get-owner request) :timestamp (java.util.Date.)))
        (response/found "/")))))

(defn update-message! [{:keys [params] :as request}]
  (let [id             {:id  (Integer. (:id params))}
        random-prefix  (str (rand-int 1000000) "-conus-")
        fixed-params   (fix-params params random-prefix)
        updated-map    (select-keys fixed-params [:name :description :askingprice :producturl :imageurl])
        _              (upload-file-helper! fixed-params random-prefix)]
    (if (:imageurl updated-map)
      (db/update-thing! (conj updated-map id))
      (db/update-thing-without-picture! (conj updated-map id)))))


(defn delete-thing! [{:keys [params] :as request}]
  (let [id             {:id  (Integer. (:id params))}]
    (db/delete-thing! id)))

(defn about-page []
  (layout/render "about.html"))

(defn user-list []
  (layout/render "user.html"
                 {:messages (map :login (reverse (sort-by :timestamp (db/get-users))))}))

(defn user-page [user]
  (layout/render "user-page.html"
                 {:messages (db/get-things-by-owner {:login user}) :user user :email user}))


(defn user-product-page [user user-product request]
  (let [params {:thing (db/get-thing-by-login-and-name {:login user :name user-product})
                :user user
                :name user-product}
        owner (get-owner request)]
    (if (:ignore-http env)
      (layout/render "user-product-page.html"
                     params)
      (if (= {:id owner}
             (db/get-id-from-login {:login user}))
        (if (#{1 2 3 5} owner) ;; if the user is any of the founders, let them edit the aal
          (layout/render "user-product-page-with-aal-editing.html"
                         params)
          (layout/render "user-product-page.html"
                         params))
        (if (#{1 2 3 5} owner)
          (layout/render "user-product-page-no-editing-with-aal-editing.html"
                         params)
          (layout/render "user-product-page-no-editing.html"
                         params))))))

(defn check-oauth [page]
  (if (:ignore-http env)
    page
    (friend/authorize #{:conus.middleware/user} page)))

(defn tags
  ([tag]
   (layout/render "tagged-things.html"
                  {:things (encode-urls (db/get-things-from-description {:tag (str "%" tag "%")}))}))
  ([tag user]
   (layout/render "tagged-things.html"
                  {:things (encode-urls (db/get-things-from-description-and-login {:tag (str "%" tag "%") :login user}))})))

(defn add-aal!  [{:keys [params] :as request}]
  (db/add-aal! {:id (:id params) :aal (:aal params)}))

(defroutes home-routes
  ;; you can view the home page, and view and share links to products without being logged in.
  (GET "/user/:user/:user-product" [user user-product :as request] (user-product-page user user-product request))
  (GET "/" request (home-page request))
  (GET "/tag/:tag" [tag :as request] (tags tag))
  (GET "/user/:user/tag/:tag" [tag user :as request] (tags tag user))

  ;; for anything else, you need to be logged in.
  (POST "/aal" request (add-aal! request)
        (redirect "/"))

  (POST "/" request   (check-oauth (save-message! request)))
  (POST "/user/:user" [user :as request] (check-oauth (save-message! request))
        (redirect (str "/user/" user)))
  (POST "/user/:user/:user-product-page" [user-product-page user :as request] (check-oauth (update-message! request))
        (redirect (str "/user/" user)))
  (POST "/user/:user/:user-product-page/delete" [user-product-page user :as request] (check-oauth (delete-thing! request))
        (redirect (str "/user/" user)))
  (GET "/user" request (check-oauth (user-list)))
  (GET "/user/:user" [user]  (check-oauth (user-page user))))
