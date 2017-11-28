(ns ulkoiset-rajapinnat.organisaatio
  (:require [manifold.deferred :as d]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-promise get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def organisaatio-api "%s/organisaatio-service/rest/organisaatio/v3/findbyoids")

(defn log-fetch [number-of-oids start-time response]
  (log/info "Fetching 'organisaatiot' (size =" number-of-oids ") ready with status" (response :status) "! Took " (- (System/currentTimeMillis) start-time) "ms!")
  response)

(defn fetch-organisations-for-oids [config organisation-oids]
  (if (> (count organisation-oids) 1000)
    (throw (new RuntimeException "Can only fetch 1000 orgs at once!")))
  (if (empty? organisation-oids)
    (let [deferred (d/deferred)]
      (d/success! deferred [])
      deferred)
    (let [host (-> config :organisaatio-host-virkailija)
          url (format organisaatio-api host)
          start-time (System/currentTimeMillis)
          promise (-> (post-json-as-promise url organisation-oids)
              (d/chain (partial log-fetch (count organisation-oids) start-time))
              (d/chain parse-json-body))]
      promise)))

(defn fetch-organisations-in-batch [config organisation-oids]
    (d/loop [oid-batches (partition-all 500 organisation-oids)
             fetched (vector)]
            (d/chain (fetch-organisations-for-oids config (first oid-batches))
                     #(let [new-fetched (conj fetched %)
                            rest-batches (rest oid-batches)]
                        (if (empty? rest-batches)
                          new-fetched
                          (d/recur rest-batches
                                   new-fetched))))))
