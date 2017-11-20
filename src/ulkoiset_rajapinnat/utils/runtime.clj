(ns ulkoiset-rajapinnat.utils.runtime
  (:require [clojure.string :as str]))

(defn shutdown-hook [runnable]
  (.addShutdownHook (Runtime/getRuntime) (Thread. runnable)))
