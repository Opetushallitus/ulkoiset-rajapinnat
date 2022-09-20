(ns ulkoiset-rajapinnat.utils.async_safe
  (:require [clojure.core.async :as async]))

; ordinary async/map gets stuck when sources are empty! Use this function instead.
(defn async-map-safe [f sources default-value]
  (if (empty? sources)
    (async/to-chan default-value)
    (async/map f sources)))
