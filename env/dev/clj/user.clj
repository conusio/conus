(ns user
  (:require [mount.core :as mount]
            [luminus-migrations.core :as migrations]
            guestbook.core))


(defn stop []
  (mount/stop-except #'guestbook.core/repl-server))

(defn start []
  (mount/start #'guestbook.config/env)
  (migrations/migrate ["migrate"] (select-keys guestbook.config/env [:database-url]))
  ;; the above mount/start and migrations/migrate is a hack, to avoid having to run `lein run migrate`
  ;; before starting the app for the first time in the repl. http://www.luminusweb.net/docs
  ;; i do this also because `lein run migrate` depends on the :migratus config in project.clj.
  ;; i want migrations to depend on variables set in dev's, test's, or prod's config.edn, not project.clj.
  (stop)
  (mount/start-without #'guestbook.core/repl-server))

(defn restart []
  (stop)
  (start))


