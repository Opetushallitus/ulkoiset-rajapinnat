(ns ulkoiset-rajapinnat.vastaanotto
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def valinta-tulos-service-api "%s/valinta-tulos-service/haku/%s")

(comment
hakijan_lopullinen_jonosija
hakijan_jonosijan_tarkenne
valinnan_tilan_lisatieto
hyvaksytty_ensikertalaisten_hakijaryhmasta
hyvaksytty_harkinnanvaraisesti
yhteispisteet
alin_hyvaksytty_pistemaara
osallistui_paasykokeeseen
osallistui_soveltuvuuskokeeseen
osallistui_kielikokeeseen
urheilijan_lisapisteet)

(defn transform-hakutoive [hakutoive]
  {"hakukohde_oid" (hakutoive "hakukohdeOid")
   "valinnan_tila" (hakutoive "valintatila")
   "valintatapajono" (hakutoive "valintatapajonoOid")
   "ilmoittautumisen_tila" (get-in hakutoive ["ilmoittautumistila" "ilmoittautumistila"])})

(defn transform-vastaanotto [vastaanotto]
    {"henkilo_oid" (vastaanotto "hakijaOid")
     "hakutoiveet" (map transform-hakutoive (vastaanotto "hakutoiveet"))})

(defn result-to-vastaanotto [result]
  result)

(defn fetch-vastaanotto [internal-host-virkailija haku-oid]
  (let [promise (get-as-promise (format valinta-tulos-service-api internal-host-virkailija haku-oid))]
    (chain promise parse-json-body result-to-vastaanotto)))

(defn vastaanotto-resource [config haku-oid request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (let [internal-host-virkailija (config :internal-host-virkailija)]
                  (-> (let-flow [vastaanotot (fetch-vastaanotto internal-host-virkailija haku-oid)]
                                (let [json (to-json (map transform-vastaanotto vastaanotot))]
                                  (-> channel
                                      (status 200)
                                      (body-and-close json))))
                      (catch Exception (exception-response channel))))
                (schedule-task (* 1000 60 60) (close channel))))

