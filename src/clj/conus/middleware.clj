(ns conus.middleware
  (:require [conus.env :refer [defaults]]
            [clojure.tools.logging :as log]
            [conus.layout :refer [*app-context* error-page]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [conus.config :refer [env]]
            [ring.middleware.flash :refer [wrap-flash]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :as util]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [cemerick.friend :as friend]
            [taoensso.timbre :as timbre])
  (:import [javax.servlet ServletContext]))

(def client-config
  {:client-id         "68a83e5d5441d1419199"
   :client-secret     "301641a4c8ca1818269d9571694c559e5cf31e66"
   :callback {:domain "http://localhost:3000" ;; set in config: if dev, then localhost:3000, else rely on env var
              :path "/oauthcallback"}})

(def uri-config
  {:authentication-uri {:url "https://github.com/login/oauth/authorize"
                        :query {:client_id (:client-id client-config)
                                :response_type "code"
                                :redirect_uri "http://localhost:3000/oauthcallback"
                                :scope "user"}}
   :access-token-uri {:url "https://github.com/login/oauth/access_token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :grant_type "authorization_code"
                              :redirect_uri  "http://localhost:3000/oauthcallback"}}})

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
  "Github API call for the current authenticated users repository list."
  [access-token]
  (let [url (str "https://api.github.com/user?access_token=" access-token)
        response (try  (client/get url {:accept :json})
                       (catch Exception e "You're not logged in"))
        reposa (if (= response "You're not logged in") "You're not logged in" (json/read-str (:body response) :key-fn keyword))]
    reposa))

(defn auth-user-and-save-to-db!
"i'm not sure where this should be called."
  [request]
  (let [access-token (get-token request)
        user-info    (get-user-info access-token)
        user         {:login     (:login user-info)
                      :githubid  (:id user-info)
                      :name      (:name user-info)
                      :email     (:email user-info)
                      :location  (:location user-info)
                      :timestamp (java.util.Date.)}]
    (when-not (some #{(:login user)} (flatten (map vals (conus.db.core/get-logins)))) (conus.db.core/save-user! user))))

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
  (-> ((:middleware defaults) handler)
      wrap-webjars
      wrap-flash
      (wrap-session {:cookie-attrs {:http-only true}})
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      wrap-context
      wrap-internal-error))
