(ns user
  (:require [mount.core :as mount]
            [luminus-migrations.core :as migrations]
            conus.core))


(defn stop []
  (mount/stop-except #'conus.core/repl-server))

(defn start []
  #_(mount/start #'conus.config/env)
  #_(migrations/migrate ["migrate"] (select-keys conus.config/env [:database-url]))
  ;; the above mount/start and migrations/migrate is a hack, to avoid having to run `lein run migrate`
  ;; before starting the app for the first time in the repl. http://www.luminusweb.net/docs
  ;; i do this also because `lein run migrate` depends on the :migratus config in project.clj.
  ;; i want migrations to depend on variables set in dev's, test's, or prod's config.edn, not project.clj.
  #_(stop)
  (mount/start-without #'conus.core/repl-server))

(defn restart []
  (stop)
  (start))


