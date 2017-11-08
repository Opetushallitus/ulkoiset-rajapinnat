(ns ulkoiset-rajapinnat.oppija
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def oppija-api "%s/suoritusrekisteri/rest/v1/oppijat?haku=%s")

(comment
  henkilo_oid
  yksiloity
  henkilotunnus
  syntyma_aika
  etunimet
  sukunimi
  sukupuoli_koodi
  aidinkieli
  hakijan_kotikunta
  hakijan_asuinmaa
  hakijan_kansalaisuus)


(defn transform-oppija [oppija]
  {"henkilo_oid" (oppija "oid")})

(defn fetch-oppija [internal-host-virkailija haku-oid]
  (let [promise (get-as-promise (format oppija-api internal-host-virkailija haku-oid))]
    (chain promise parse-json-body)))

(defn oppija-resource [config haku-oid request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (let [host (config :suoritusrekisteri-host)]
                  (-> (let-flow [oppijat (fetch-oppija host haku-oid)]
                                (let [json (to-json (map transform-oppija oppijat))]
                                  (-> channel
                                      (status 200)
                                      (body-and-close json))))
                      (catch Exception (exception-response channel))))
                (schedule-task (* 1000 60 60) (close channel))))

