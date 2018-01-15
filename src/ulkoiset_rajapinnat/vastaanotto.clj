(ns ulkoiset-rajapinnat.vastaanotto
  (:require [manifold.deferred :refer [let-flow catch chain zip]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.cas :refer [jsessionid-fetcher]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json post-json-with-cas post-json-as-promise get-json-with-cas parse-json-body-stream]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching get-value-if-not-nil]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def valinta-tulos-service-api "%s/valinta-tulos-service/haku/streaming/%s/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true")
(def valintaperusteet-service-api "%s/valintaperusteet-service/resources/hakukohde/avaimet")
;TODO korjaa audit-parametrit, kun CAS-autentikointi päällä
(def valintapiste-service-api "%s/valintapiste-service/api/pisteet-with-hakemusoids?sessionId=sID&uid=1.2.246.1.1.1&inetAddress=127.0.0.1&userAgent=uAgent")
(def suoritusrekisteri-service-api "%s/suoritusrekisteri/rest/v1/oppijat/?ensikertalaisuudet=false&haku=%s")

(def oppijat-batch-size 5000)
(def valintapisteet-batch-size 30000)

(defn vastaanotto-builder [kokeet valintapisteet kielikokeet]
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

  (defn- ammatilliseen-kielikokeeseen-osallistuminen [hakijan-kielikokeet]
    (let [osallistuminen (get (first (first hakijan-kielikokeet)) :osallistuminen)]
      (if (= osallistuminen "ei_osallistunut")
        false
        (if (= osallistuminen "osallistui")
          true
          nil))))

  (defn- hakutoive-builder [hakutoiveiden-kokeet hakemuksen-valintapisteet hakijan-kielikokeet]

    (fn [hakutoive]
      (let [hakutoive-oid (hakutoive "hakukohdeOid")
            hakutoiveen-kokeet (get hakutoiveiden-kokeet hakutoive-oid)
            valintatapajono (first (hakutoive "hakutoiveenValintatapajonot"))
            kielikokeiden-tunnisteet (map #(get % :tunniste) (filter #(get % :kielikoe) hakutoiveen-kokeet))
            valintakokeiden-tunnisteet (map #(get % :tunniste) (filter #(get % :valintakoe) hakutoiveen-kokeet))
            osallistuminen (osallistuminen-checker hakemuksen-valintapisteet)]

        (def kielikokeeseen-osallistuminen
          (let [muuKielikoeOsallistuminen (osallistuminen kielikokeiden-tunnisteet)
                ammatillinenKielikoeOsallistuminen (ammatilliseen-kielikokeeseen-osallistuminen hakijan-kielikokeet)]
              (if (not (nil? muuKielikoeOsallistuminen))
                muuKielikoeOsallistuminen
                ammatillinenKielikoeOsallistuminen)))

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
         "osallistui_kielikokeeseen"      kielikokeeseen-osallistuminen})))

  (fn [vastaanotto]
    (let [hakemus-oid (vastaanotto "hakemusOid")
          hakija-oid (vastaanotto "hakijaOid")
          hakemuksen-pisteet (valintapisteet hakemus-oid)
          hakijan-kielikokeet (kielikokeet hakija-oid)
          build-hakutoive (hakutoive-builder kokeet hakemuksen-pisteet hakijan-kielikokeet)]
      {"henkilo_oid" hakija-oid
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

(defn recursive-find-kielikokeet [oppijat]
  (defn- transform-arvosana [arvosana]
    (if (nil? arvosana) {} {:osallistuminen (get (get arvosana "arvio") "arvosana") :kieli (get arvosana "lisatieto")}))

  (defn- transform-suoritus [suoritus]
    (let [komo ((suoritus "suoritus") "komo")
          arvosanat (suoritus "arvosanat")]
        {:komo komo :arvosanat (map transform-arvosana arvosanat)}))

  (defn- find-kielikoe [o]
    (let [filtered (filter #(= "ammatillisenKielikoe" (get % :komo)) (map transform-suoritus (get o "suoritukset")))]
      (if (empty? filtered) {} {(get o "oppijanumero") (map #(get % :arvosanat) filtered)})))

  (if (empty? oppijat)
    {}
    (merge (find-kielikoe (first oppijat)) (recursive-find-kielikokeet (rest oppijat)))))

(defn fetch-vastaanotot [host haku-oid]
  (let [promise (get-as-promise (format valinta-tulos-service-api host haku-oid) {:as :stream})]
    (chain promise parse-json-body-stream)))

(defn fetch-valintapisteet [host kaikki-hakemus-oidit]
  (log/info (str "Hakemuksia " (count kaikki-hakemus-oidit) " kpl"))
  (def url (format valintapiste-service-api host))
  (defn- group-valintapisteet [valintapisteet] (apply merge (map (fn [p] {(p "hakemusOID") (p "pisteet")}) valintapisteet)))
  (defn- fetch-valintapisteet-hakemuksille [hakemus-oidit]
    (let [promise (post-json-as-promise url hakemus-oidit {})]
      (chain promise parse-json-body group-valintapisteet)))
  (apply zip (map #(fetch-valintapisteet-hakemuksille %) (partition valintapisteet-batch-size valintapisteet-batch-size nil kaikki-hakemus-oidit))))

(defn fetch-kokeet [fetch-jsession-id host hakukohde-oidit]
  (log/info (str "Hakukohteita " (count hakukohde-oidit) " kpl"))
  (let-flow [jsession-id (fetch-jsession-id "/valintaperusteet-service")]
    (let [promise (post-json-with-cas host jsession-id valintaperusteet-service-api hakukohde-oidit)]
      (chain promise recursive-find-valintakokeet))))

(defn fetch-ammatilliset-kielikokeet [fetch-jsession-id host haku-oid kaikki-oppijanumerot]
  (log/info (str "Oppijanumeroita " (count kaikki-oppijanumerot) " kpl"))
  (defn- fetch-ammatilliset-kielikokeet-oppijanumeroille [jsession-id oppijanumerot]
    (let [promise (post-json-with-cas (format suoritusrekisteri-service-api host haku-oid) jsession-id oppijanumerot)]
      (chain promise recursive-find-kielikokeet)))
  (let-flow [jsession-id (fetch-jsession-id "/suoritusrekisteri")]
            (apply zip (map #(fetch-ammatilliset-kielikokeet-oppijanumeroille jsession-id %) (partition oppijat-batch-size oppijat-batch-size nil kaikki-oppijanumerot)))))

(defn vastaanotto-resource [config haku-oid request channel]
  (log/info (str "oppijat batch size = " oppijat-batch-size))
  (let [vastaanotto-host-virkailija (config :vastaanotto-host-virkailija)
        valintapiste-host-virkailija (config :valintapiste-host-virkailija)
        host-virkailija (config :host-virkailija)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (-> (let-flow [vastaanotot (fetch-vastaanotot vastaanotto-host-virkailija haku-oid)
                   hakukohde-oidit (distinct (map #(% "hakukohdeOid") (flatten (map #(% "hakutoiveet") vastaanotot))))
                   hakemus-oidit (map #(% "hakemusOid") vastaanotot)
                   fetch-jsession-id (jsessionid-fetcher host-virkailija username password)
                   valintakokeet (fetch-kokeet fetch-jsession-id host-virkailija hakukohde-oidit)
                   valintapisteet (fetch-valintapisteet valintapiste-host-virkailija hakemus-oidit)
                   oppijanumerot (map #(% "hakijaOid") vastaanotot)
                   kielikokeet (fetch-ammatilliset-kielikokeet  fetch-jsession-id host-virkailija haku-oid oppijanumerot)]
                  (log/info (str "Hakukohde_oidit=" (count hakukohde-oidit)))
                  (log/info hakukohde-oidit)
                  (log/info (str "Hakemus_oidit=" (count hakemus-oidit)))
                  (log/info hakemus-oidit)
                  (log/info (str "Valintakokeet=" (count valintakokeet)))
                  ;(log/info valintakokeet)
                  (log/info (str "Valintapisteet=" (count valintapisteet)))
                  ;(log/info valintapisteet)
                  (log/info (str "Kielikokeet=" (count kielikokeet)))
                  ;(log/info (apply merge kielikokeet))
                  (let [build-vastaanotto (vastaanotto-builder valintakokeet (into {} valintapisteet) (apply merge kielikokeet))
                        json (to-json (map build-vastaanotto vastaanotot))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))