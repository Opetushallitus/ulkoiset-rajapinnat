(ns ulkoiset-rajapinnat.vastaanotto
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def valinta-tulos-service-api "%s/valinta-tulos-service/haku/streaming/%s/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true")

(comment
  hakijan_lopullinen_jonosija
  hakijan_jonosijan_tarkenne
  valinnan_tilan_lisatieto
  hyvaksytty_ensikertalaisten_hakijaryhmasta x
  hyvaksytty_harkinnanvaraisesti
  yhteispisteet
  alin_hyvaksytty_pistemaara
  osallistui_paasykokeeseen
  osallistui_soveltuvuuskokeeseen
  osallistui_kielikokeeseen
  urheilijan_lisapisteet)

(defn transform-hyvaksytty-ensikertalaisten-hakijaryhmasta [hakijaryhmat]
  (def ensikertalaisen_hakijaryhma (first (filter #(= (% "hakijaryhmatyyppikoodiUri") "hakijaryhmantyypit_ensikertalaiset") hakijaryhmat)))
  (if (nil? ensikertalaisen_hakijaryhma)
    nil
    (ensikertalaisen_hakijaryhma "hyvaksyttyHakijaryhmasta")))

(defn transform-hakutoive [hakutoive]
  (def valintatapajono (first (hakutoive "hakutoiveenValintatapajonot")))

  {"hakukohde_oid"                  (hakutoive "hakukohdeOid")
   "valinnan_tila"                  (valintatapajono "tila")
   "valinnan_tilan_lisatieto"       ((valintatapajono "tilanKuvaukset") "FI")
   "valintatapajono"                (valintatapajono "valintatapajonoOid")
   "hakijan_lopullinen_jonosija"    (valintatapajono "jonosija")
   "hakijan_jonosijan_tarkenne"     (valintatapajono "tasasijaJonosija")
   "yhteispisteet"                  (valintatapajono "pisteet")
   "ilmoittautumisen_tila"          (valintatapajono "ilmoittautumisTila")
   "vastaanoton_tila"               (hakutoive "vastaanottotieto")
   "alin_hyvaksytty_pistemaara"     (valintatapajono "alinHyvaksyttyPistemaara")
   "hyvaksytty_harkinnanvaraisesti" (valintatapajono "hyvaksyttyHarkinnanvaraisesti")
   "hyvaksytty_ensikertalaisten_hakijaryhmasta" (transform-hyvaksytty-ensikertalaisten-hakijaryhmasta (hakutoive "hakijaryhmat"))})

(defn transform-vastaanotto [vastaanotto]
  {"henkilo_oid" (vastaanotto "hakijaOid")
   "hakutoiveet" (map transform-hakutoive (vastaanotto "hakutoiveet"))})

(defn result-to-vastaanotto [result]
  result)

(defn fetch-vastaanotto [internal-host-virkailija haku-oid]
  (let [promise (get-as-promise (format valinta-tulos-service-api internal-host-virkailija haku-oid))]
    (chain promise parse-json-body result-to-vastaanotto)))

(defn vastaanotto-resource [config haku-oid request channel]
  (let [vastaanotto-host-virkailija (config :vastaanotto-host-virkailija)]
    (-> (let-flow [vastaanotot (fetch-vastaanotto vastaanotto-host-virkailija haku-oid)]
                  (let [json (to-json (map transform-vastaanotto vastaanotot))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))

