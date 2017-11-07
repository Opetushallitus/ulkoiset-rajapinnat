(ns ulkoiset-rajapinnat.utils.runtime
  (:require [clojure.string :as str]
            [clj-log4j2.core :as log]))

(defn shutdown-hook [runnable]
  (.addShutdownHook (Runtime/getRuntime) (Thread. runnable)))
