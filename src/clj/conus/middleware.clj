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
            #_[cemerick.friend [workflows :as workflows]
             [credentials :as creds]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [cemerick.friend :as friend]
            [taoensso.timbre :as timbre]
            )
  (:import [javax.servlet ServletContext]))
(def oauth2-config
  {:twitter
   {:authorize-uri    "https://api.twitter.com/oauth/authorize"
    :access-token-uri "https://api.twitter.com/oauth/access_token"
    :client-id        "uFRreriTqef12qT1kwIQsz0as"
    :client-secret    "a1VS3EnuXpXlNmUME0HEuqtvt7cdQWGEKfIp1gozt57aje6iHx"
    :scopes           ["user:email"]
    :launch-uri       "/oauth2/twitter"
    :redirect-uri     "/oauth2/twitter/callback"
    :landing-uri      "/"}})

(def client-config
  {:client-id         "68a83e5d5441d1419199"
   :client-secret     "301641a4c8ca1818269d9571694c559e5cf31e66"
   :callback {:domain "http://localhost:3000" #_(format "%s://%s:%s"
                              (:protocol parsed-url)
                              (:host parsed-url)
                              (:port parsed-url))
              :path "/oauthcallback" #_(:path parsed-url)}})

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
  (let [_ (timbre/info "credential-fn is being called")]
    ;;lookup token in DB or whatever to fetch appropriate :roles
    {:identity token :roles #{:conus.middleware/user}}))


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
      (:access-token (nth (keys authentications) index)))))

(defn render-status-page [request]
  (let [count (:count (:session request) 0)
        session (assoc (:session request) :count (inc count))]
    (-> (str "<p>We've hit the session page "
             (:count session)
             " times.</p><p>The current session: "
             session
             "</p>")
        (ring.util.response/response)
        (assoc :session session))))

(defn get-github-repos
  "Github API call for the current authenticated users repository list."
  [access-token]
  (let [url (str "https://api.github.com/user/repos?access_token=" access-token)
        response (client/get url {:accept :json})
        repos (json/read-str (:body response) :key-fn keyword)]
    repos))

(defn render-repos-page
  "Shows a list of the current users github repositories by calling the github api
   with the OAuth2 access token that the friend authentication has retrieved."
  [request]
  (let [access-token (get-token request)
        repos-response (get-github-repos access-token)]
    (->> repos-response
         (map :name)
         (vec)
         (str))))

(def users {"root" {:username "root"
                    :password "admin_password"#_(creds/hash-bcrypt "admin_password")
                    :roles #{::admin}}
            "jane" {:username "jane" 
                    :password "user-password" #_(creds/hash-bcrypt "user_password")
                    :roles #{::user}}})


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
      wrap-internal-error
      ))
