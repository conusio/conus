(ns conus.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[conus started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[conus has shut down successfully]=-"))
   :middleware identity})
