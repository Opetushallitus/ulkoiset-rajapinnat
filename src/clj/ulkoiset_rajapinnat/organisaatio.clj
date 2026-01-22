(ns ulkoiset-rajapinnat.organisaatio
  (:require [clojure.core.async :as async]
            [full.async :refer :all]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-channel parse-json-body-stream]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.async_safe :refer :all]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(defn- log-fetch [number-of-oids start-time response]
  (log/info "Fetching 'organisaatiot' (size =" number-of-oids ") ready with status" (response :status) "! Took " (- (System/currentTimeMillis) start-time) "ms!")
  response)

(def organisaatio-batch-size 500)

(defn fetch-organisations-in-batch-channel
  ([organisation-oids]
   (if (empty? organisation-oids)
     (async/go [])
     (go-try
       (let [url (resolve-url :organisaatio-service.find-by-oids)
             partitions (partition-all organisaatio-batch-size organisation-oids)
             post (fn [x] (let [start-time (System/currentTimeMillis)
                                mapper (comp parse-json-body-stream (partial log-fetch (count organisation-oids) start-time))]
                            (if (> (count x) 1000)
                              (throw (new RuntimeException "Can only fetch 1000 orgs at once!")))
                            (post-json-as-channel url x mapper)))
             organisaatiot (<? (async-map-safe vector (map #(post %) partitions) []))]
         (apply concat organisaatiot))))))
