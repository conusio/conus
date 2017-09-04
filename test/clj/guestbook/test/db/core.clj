(ns guestbook.test.db.core
  (:require [guestbook.db.core :refer [*db*] :as db]
            [luminus-migrations.core :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [guestbook.config :refer [env]]
            [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'guestbook.config/env
      #'guestbook.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-can-add-something-to-db
  "unfortunately, luminus-guestbook was designed to be tested from `lein test`,
not from the repl. so we can't guarantee a clean dev db when testing from repl.
so we test that we can add transactions to the db, not whether an empty db can
have one transaction added."
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (let [message {:name "test"
                   :description "test"
                   :askingprice "100"
                   :producturl "producturl"
                   :imageurl "imageurl"
                   :email "m@m.com"
                   :timestamp (java.util.Date.)}
          messages-before-tx (db/get-messages t-conn {})]
      (is (= 1 (db/save-message! t-conn message)))
      (let [messages-after-tx (db/get-messages t-conn {})
            _ (clojure.tools.logging/info messages-after-tx)]
        (is (> (count messages-after-tx) (count messages-before-tx)))
        (is (= (dissoc message :timestamp) (dissoc (first messages-after-tx) :id :timestamp)))))))
