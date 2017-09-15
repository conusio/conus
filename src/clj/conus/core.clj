(ns conus.core
  (:require [conus.handler :as handler]
            [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [luminus-migrations.core :as migrations]
            [conus.config :refer [env]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [clojure.java.io :as io])
  (:import
   (java.security KeyStore)
   (java.util TimeZone)
   (org.joda.time DateTimeZone))
  (:gen-class))


(def ssl-options
  {:port              443
   :keystore          "keystore.jks"
   :keystore-password (get (System/getenv) "KEYSTORE_PASSWORD")})

(defn keystore [file pass]
  (doto (KeyStore/getInstance "JKS")
    (.load (io/input-stream (io/file file)) (.toCharArray pass))))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop}
  http-server
  :start
  (http/start
   (merge
    (-> env
        (assoc :handler (handler/app))
        (update :port #(or (-> env :options :port) %)))
    (if (:ignore-http env)
      {}
      {:port         nil          ;disables access on HTTP port
       :ssl-port     (:port ssl-options)
       :keystore     (keystore (:keystore ssl-options) (:keystore-password ssl-options))
       :key-password (:keystore-password ssl-options)})))
  :stop
  (http/stop http-server))


(mount/defstate ^{:on-reload :noop}
                repl-server
                :start
                (when-let [nrepl-port (env :nrepl-port)]
                  (repl/start {:port nrepl-port}))
                :stop
                (when repl-server
                  (repl/stop repl-server)))


(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))
(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (migrations/migrate ["migrate"] (select-keys env [:database-url]))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& args]
  (cond
    (some #{"migrate" "rollback"} args)
    (do
      (mount/start #'conus.config/env)
      (migrations/migrate args (select-keys env [:database-url]))
      (System/exit 0))
    :else
    (start-app args)))
  
