(ns ulkoiset-rajapinnat.rest
  "Rest Utils"
  (:require [manifold.deferred :as d]
            [org.httpkit.client :as http]))

(defn get-as-promise [url]
  (let [deferred (d/deferred)]
    (http/get url {} (fn [resp]
                       (d/success! deferred resp)
                       ))
    deferred))
