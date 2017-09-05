(ns conus.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [conus.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[conus started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[conus has shut down successfully]=-"))
   :middleware wrap-dev})
