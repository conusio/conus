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

(def sample-user
  {:id 1 :name "the hedonist" :email "h@h.com" :login "hedonist" :location "hell" :timestamp (java.util.Date.) :githubid 39})

(def sample-thing
  {:name "ipad"
   :description "an ipad"
   :askingprice "200"
   :imageurl ""
   :producturl "whizzy.com"
   :owner 1
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

;; this should be a test in db test!!!!
#_(defn update-message! [{:keys [params] :as request}]
  (let [thing-map {;; :id 9,
                   :name "testman",
                   :description "is the bestman",
                   :askingprice "69420",
                   :producturl "producturlman",
                   :imageurl "gooseberg"}
        updated-thing-map {;; :id 9,
                           :name "updated-thing-name",
                           :description "updated-description",
                           :askingprice "196969",
                           :producturl "www.updated-example.com",
                           :imageurl "/images/416744-conus-Sheafer Snorkel.jpg "}
        thing-id (db/get-id-of-thing thing-map)]
    #_(db/update-name-and-description! (conj updated-thing-map thing-id))
    )
  )
