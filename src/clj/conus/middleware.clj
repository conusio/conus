(ns conus.middleware
  (:require [conus.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [conus.layout :refer [*app-context* error-page]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.ssl :refer [wrap-ssl-redirect]]
            [conus.config :refer [env]]
            [ring.middleware.flash :refer [wrap-flash]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :as util]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [cemerick.friend :as friend]
            [taoensso.timbre :as timbre]
            [conus.db.core :as db])
  (:import [javax.servlet ServletContext]))

(def client-config
  {:client-id  (or  (get (System/getenv) "CONUS_GITHUB_CLIENT_ID") "dummy") ;; HACK
   :client-secret (or  (get (System/getenv) "CONUS_GITHUB_CLIENT_SECRET") "dummy") ;; HACK
   :callback {:domain "https://conus.io"
              :path "/oauthcallback"}})

(def uri-config
  {:authentication-uri {:url "https://github.com/login/oauth/authorize"
                        :query {:client_id (:client-id client-config)
                                :response_type "code"
                                :redirect_uri "https://conus.io/oauthcallback"
                                :scope "read:user"}}
   :access-token-uri {:url "https://github.com/login/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :grant_type "authorization_code"
                              :redirect_uri  "https://conus.io/oauthcallback"}}})

(defn credential-fn
  [token]
  ;;lookup token in DB or whatever to fetch appropriate :roles
  {:identity token :roles #{:conus.middleware/user}})


(def workflow
  (oauth2/workflow
   {:client-config client-config
    :uri-config uri-config
    :access-token-parsefn util/get-access-token-from-params
    :credential-fn credential-fn}))

(def auth-opts
  {:allow-anon? true
   :workflows [workflow]})

(defn get-authentications
  [request]
  (get-in request [:session :cemerick.friend/identity :authentications]))

(defn get-token
  ([request]
    (get-token request 0))
  ([request index]
    (let [authentications (get-authentications request)]
      (:access-token (nth (keys authentications) index))))) ;; i think the default value of index being 0 is a bug. it means that if some access-tokens become invalid, it'll choose the *oldest* access-token, not the most recent.

(defn get-user-info
  [access-token]
  (let [url (str "https://api.github.com/user?access_token=" access-token)
        response (try  (client/get url {:accept :json})
                       (catch Exception e "You're not logged in"))
        reposa (if (= response "You're not logged in") "You're not logged in" (json/read-str (:body response) :key-fn keyword))]
    reposa))

(defn wrap-auth-user-and-save-to-db!
  [handler]
  (fn [request]
    (log/info "wrap-auth-user-and-save-to-do! was called")
    (if (:ignore-http env)
      (log/info "ignoring http.")
      (if-let [access-token  (get-token request)]
        (if-let [user-info  (get-user-info access-token)]
          (let  [user     {:login     (:login user-info)
                           :githubid  (:id user-info)
                           :name      (:name user-info)
                           :email     (:email user-info)
                           :location  (:location user-info)
                           :timestamp (java.util.Date.)}]
            (when-not (contains? (set (flatten (map vals (db/get-logins)))) (:login user))
              (do
                (db/save-user! user)
                (log/info "(db/save-user!) was called")))))))
    (handler request)))

(defn wrap-context [handler]
  (fn [request]
    (binding [*app-context*
              (if-let [context (:servlet-context request)]
                ;; If we're not inside a servlet environment
                ;; (for example when using mock requests), then
                ;; .getContextPath might not exist
                (try (.getContextPath ^ServletContext context)
                     (catch IllegalArgumentException _ context))
                ;; if the context is not specified in the request
                ;; we check if one has been specified in the environment
                ;; instead
                (:app-context env))]
      (handler request))))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t)
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :transit-json :transit-msgpack]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn wrap-base [handler]
  (if (:ignore-http env)
    (-> ((:middleware defaults) handler)
        wrap-auth-user-and-save-to-db!
        wrap-webjars
        wrap-flash
        (wrap-session {:cookie-attrs {:http-only true}})
        (wrap-defaults
         (-> site-defaults
             (assoc-in [:security :anti-forgery] false)
             (dissoc :session)))
        wrap-context
        wrap-internal-error)
    (-> ((:middleware defaults) handler)
        wrap-auth-user-and-save-to-db!
        wrap-ssl-redirect
        wrap-webjars
        wrap-flash
        (wrap-session {:cookie-attrs {:http-only true}})
        (wrap-defaults
         (-> site-defaults
             (assoc-in [:security :anti-forgery] false)
             (dissoc :session)))
        wrap-context
        wrap-internal-error)))
