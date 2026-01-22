(ns ulkoiset-rajapinnat.utils.runtime)

(defn shutdown-hook [runnable]
  (.addShutdownHook (Runtime/getRuntime) (Thread. runnable)))
