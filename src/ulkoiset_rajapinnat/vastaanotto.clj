(ns ulkoiset-rajapinnat.vastaanotto
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.cas :refer [jsessionid-fetcher]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json post-json-with-cas post-json-as-promise]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching get-value-if-not-nil]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def valinta-tulos-service-api "%s/valinta-tulos-service/haku/streaming/%s/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true")
(def valintaperusteet-service-api "%s/valintaperusteet-service/resources/hakukohde/avaimet")
(def valintapiste-service-api "%s/valintapiste-service/api/pisteet-with-hakemusoids?sessionId=sID&uid=1.2.246.1.1.1&inetAddress=127.0.0.1&userAgent=uAgent")

(defn vastaanotto-builder [kokeet valintapisteet]
  (defn- hyvaksytty-ensikertalaisen-hakijaryhmasta [hakijaryhmat]
    (let [ensikertalaisen-hakijaryhma (find-first-matching "hakijaryhmatyyppikoodiUri" "hakijaryhmantyypit_ensikertalaiset" hakijaryhmat)]
      (get-value-if-not-nil "hyvaksyttyHakijaryhmasta" ensikertalaisen-hakijaryhma)))

  (defn- osallistuminen-checker [hakemuksen-valintapisteet]
    (defn- kokeeseen-osallistuminen [tunniste]
      (let [kokeen-valintapisteet (find-first-matching "tunniste" tunniste hakemuksen-valintapisteet)]
        (get-value-if-not-nil "osallistuminen" kokeen-valintapisteet)))

    (fn [tunnisteet]
      (let [osallistumiset (map kokeeseen-osallistuminen tunnisteet)]
        (if (some #(= "EI_OSALLISTUNUT" %) osallistumiset)
          false
          (if (some #(= "OSALLISTUI" %) osallistumiset) true nil)))))

  (defn- hakutoive-builder [hakutoiveiden-kokeet hakemuksen-valintapisteet]

    (fn [hakutoive]
      (let [hakutoive-oid (hakutoive "hakukohdeOid")
            hakutoiveen-kokeet (get hakutoiveiden-kokeet hakutoive-oid)
            valintatapajono (first (hakutoive "hakutoiveenValintatapajonot"))
            kielikokeiden-tunnisteet (map #(get % :tunniste) (filter #(get % :kielikoe) hakutoiveen-kokeet))
            valintakokeiden-tunnisteet (map #(get % :tunniste) (filter #(get % :valintakoe) hakutoiveen-kokeet))
            osallistuminen (osallistuminen-checker hakemuksen-valintapisteet)]

        {"hakukohde_oid"                  hakutoive-oid
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
         "hyvaksytty_ensikertalaisten_hakijaryhmasta" (hyvaksytty-ensikertalaisen-hakijaryhmasta (hakutoive "hakijaryhmat"))
         "osallistui_paasykokeeseen"      (osallistuminen valintakokeiden-tunnisteet)
         "osallistui_kielikokeeseen"      (osallistuminen kielikokeiden-tunnisteet)})))

  (fn [vastaanotto]
    (let [hakemus-oid (vastaanotto "hakemusOid")
          hakemuksen-pisteet (valintapisteet hakemus-oid)
          build-hakutoive (hakutoive-builder kokeet hakemuksen-pisteet)]

      {"henkilo_oid" (vastaanotto "hakijaOid")
       "hakutoiveet" (map build-hakutoive (vastaanotto "hakutoiveet"))})))

(defn recursive-find-valintakokeet [valintaperusteet]
  (defn- transform-dto [dto]
    (let [tyyppi (dto "syötettavanArvonTyyppi")
          uri (if (nil? tyyppi) nil (tyyppi "uri"))]
      { :tunniste (dto "tunniste") :kielikoe (= uri "syotettavanarvontyypit_kielikoe") :valintakoe (= uri "syotettavanarvontyypit_valintakoe")}))

  (defn- find-valintakoe [vp]
    (let [filtered (filter (fn [v] (or (v :kielikoe) (v :valintakoe)) ) (map transform-dto (vp "valintaperusteDTO")))]
      (if (empty? filtered) {} {(vp "hakukohdeOid") filtered})))

  (if (empty? valintaperusteet)
    {}
    (merge (find-valintakoe (first valintaperusteet)) (recursive-find-valintakokeet (rest valintaperusteet)))))

(defn fetch-vastaanotot [host haku-oid]
  (let [promise (get-as-promise (format valinta-tulos-service-api host haku-oid))]
    (chain promise parse-json-body)))

(defn fetch-valintapisteet [host hakemus-oidit]
  (defn- group-valintapisteet [valintapisteet] (apply merge (map (fn [p] {(p "hakemusOID") (p "pisteet")}) valintapisteet)))
  (let [promise (post-json-as-promise (format valintapiste-service-api host) hakemus-oidit {})]
    (chain promise parse-json-body group-valintapisteet)))

(defn fetch-kokeet [fetch-jsession-id host hakukohde-oidit]
  (let-flow [jsession-id (fetch-jsession-id "/valintaperusteet-service")]
    (let [promise (post-json-with-cas host jsession-id valintaperusteet-service-api hakukohde-oidit)]
      (chain promise recursive-find-valintakokeet))))

(defn vastaanotto-resource [config haku-oid request channel]
  (let [vastaanotto-host-virkailija (config :vastaanotto-host-virkailija)
        vastaanotto-host-internal (config :vastaanotto-host-internal)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (-> (let-flow [vastaanotot (fetch-vastaanotot vastaanotto-host-virkailija haku-oid)
                   hakukohde-oidit (distinct (map #(% "hakukohdeOid") (flatten (map #(% "hakutoiveet") vastaanotot))))
                   hakemus-oidit (map #(% "hakemusOid") vastaanotot)
                   fetch-jsession-id (jsessionid-fetcher vastaanotto-host-virkailija  username password)
                   valintakokeet (fetch-kokeet fetch-jsession-id vastaanotto-host-virkailija hakukohde-oidit)
                   valintapisteet (fetch-valintapisteet vastaanotto-host-internal hakemus-oidit)]
                  (let [build-vastaanotto (vastaanotto-builder valintakokeet valintapisteet)
                        json (to-json (map build-vastaanotto vastaanotot))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))