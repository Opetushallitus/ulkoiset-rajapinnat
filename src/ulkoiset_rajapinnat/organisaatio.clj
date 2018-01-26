(ns ulkoiset-rajapinnat.organisaatio
  (:require [clojure.string :as str]
            [clojure.core.async :as async]
            [full.async :refer :all]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-channel parse-json-body-stream]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def organisaatio-api "%s/organisaatio-service/rest/organisaatio/v3/findbyoids")

(defn log-fetch [number-of-oids start-time response]
  (log/info "Fetching 'organisaatiot' (size =" number-of-oids ") ready with status" (response :status) "! Took " (- (System/currentTimeMillis) start-time) "ms!")
  response)

(def organisaatio-batch-size 500)

(defn fetch-organisations-in-batch-channel
  ([config organisation-oids]
   (go-try
     (let [host (config :organisaatio-host-virkailija)
           url (format organisaatio-api host)
           partitions (partition-all organisaatio-batch-size organisation-oids)
           post (fn [x] (let [start-time (System/currentTimeMillis)
                              mapper (comp parse-json-body-stream (partial log-fetch (count organisation-oids) start-time))]
                          (if (> (count x) 1000)
                            (throw (new RuntimeException "Can only fetch 1000 orgs at once!")))
                          (post-json-as-channel url x mapper)))
           organisaatiot (<? (async/map vector (map #(post %) partitions)))]
       (apply merge organisaatiot)))))