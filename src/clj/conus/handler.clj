(ns conus.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [conus.layout :refer [error-page]]
            [conus.routes.home :refer [home-routes service-routes]]
            [compojure.route :as route]
            [conus.env :refer [defaults]]
            [mount.core :as mount]
            [conus.middleware :as middleware]
            [cemerick.friend :as friend]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
   (-> #'home-routes
        (friend/authenticate conus.middleware/auth-opts)
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
   (-> #'service-routes
       (wrap-routes middleware/wrap-formats))
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))


(defn app [] (middleware/wrap-base #'app-routes))
