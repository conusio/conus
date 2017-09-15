(ns user
  (:require [mount.core :as mount]
            [luminus-migrations.core :as migrations]
            [conus.db.core :as db]
            [conus.middleware :as mid]
            [conus.config :refer [env]]
            conus.core))


(defn stop []
  (mount/stop-except #'conus.core/repl-server))

(defn start []
  (mount/start #'conus.config/env)
  (migrations/migrate ["migrate"] (select-keys conus.config/env [:database-url]))
  ;; the above mount/start and migrations/migrate is a hack, to avoid having to run `lein run migrate`
  ;; before starting the app for the first time in the repl. http://www.luminusweb.net/docs
  ;; i do this also because `lein run migrate` depends on the :migratus config in project.clj.
  ;; i want migrations to depend on variables set in dev's, test's, or prod's config.edn, not project.clj.
  (stop)
  (mount/start-without #'conus.core/repl-server))

(defn restart []
  (stop)
  (start))


(def fixture-thing-data
  {:name "ipad"
   :description "an ipad"
   :askingprice "200"
   :imageurl ".png"
   :producturl "lemonparty.org"
   :owner 1 ;; uhh
   :timestamp (java.util.Date.)})


(defn conus-table->things-table []
  (for [x (db/get-messages)]
    (let [owner (condp = (:email x)
                  "matt@example.com"     1
                  "elliott@example.com"  2
                  "spencer@example.com"  3)]
      (assoc x :login (:email x) :location "" :githubid "" :owner owner))))

(defn put-converted-conus-table-into-mysql []
  (for [x (conus-table->things-table)]
    (db/save-thing! x)))
