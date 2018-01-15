(ns ulkoiset-rajapinnat.oppija
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def oppija-api "%s/suoritusrekisteri/rest/v1/oppijat?haku=%s&ticket=%s")

(s/defschema Oppija
  {:henkilo_oid s/Str})

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

(defn fetch-oppija [internal-host-virkailija haku-oid service-ticket]
  (let [promise (get-as-promise (format oppija-api internal-host-virkailija haku-oid service-ticket)
                                {:headers {"CasSecurityTicket" service-ticket}})]
    (chain promise parse-json-body)))

(defn oppija-resource [config haku-oid request channel]
  (let [host (config :suoritusrekisteri-host)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (-> (let-flow [service-ticket (fetch-service-ticket
                                    host
                                    "/suoritusrekisteri"
                                    username
                                    password)
                   oppijat (fetch-oppija host haku-oid service-ticket)]
                  (let [json (to-json (map transform-oppija oppijat))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))

