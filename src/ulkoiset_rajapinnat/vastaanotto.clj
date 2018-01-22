(ns ulkoiset-rajapinnat.vastaanotto
  (:require [full.async :refer :all]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [clojure.core.async :as async]
            [ulkoiset-rajapinnat.utils.cas :refer [jsessionid-fetcher-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [post-as-channel get-as-channel status body-and-close exception-response to-json parse-json-body-stream]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching get-value-if-not-nil]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(s/defschema Vastaanotto
  {:henkilo_oid s/Str
   (s/optional-key :hakutoiveet) {
                                  (s/optional-key :hakukohde_oid) s/Any
                                  (s/optional-key :valinnan_tila) s/Any
                                  (s/optional-key :valinnan_tilan_lisatieto) s/Any
                                  (s/optional-key :valintatapajono) s/Any
                                  (s/optional-key :hakijan_lopullinen_jonosija) s/Any
                                  (s/optional-key :hakijan_jonosijan_tarkenne) s/Any
                                  (s/optional-key :yhteispisteet) s/Any
                                  (s/optional-key :ilmoittautumisen_tila) s/Any
                                  (s/optional-key :vastaanoton_tila) s/Any
                                  (s/optional-key :alin_hyvaksytty_pistemaara) s/Any
                                  (s/optional-key :hyvaksytty_harkinnanvaraisesti) s/Any
                                  (s/optional-key :hyvaksytty_ensikertalaisten_hakijaryhmasta) s/Any
                                  (s/optional-key :osallistui_paasykokeeseen) s/Any
                                  (s/optional-key :osallistui_kielikokeeseen) s/Any
                                  }})

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
    (let [osallistuminen (get (first hakijan-kielikokeet) :osallistuminen)]
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

(defn find-valintakokeet [valintaperusteet]
  (defn- transform-dto [dto]
    (let [tyyppi (dto "syötettavanArvonTyyppi")
          uri (if (nil? tyyppi) nil (tyyppi "uri"))]
      { :tunniste (dto "tunniste") :kielikoe (= uri "syotettavanarvontyypit_kielikoe") :valintakoe (= uri "syotettavanarvontyypit_valintakoe")}))

  (defn- uri [dto]
    (let [tyyppi (dto "syötettavanArvonTyyppi")]
      uri (if (nil? tyyppi) nil (tyyppi "uri"))))

  (defn- find-valintaperusteen-kokeet [valintaperuste]
    (let [hakukohteen-kokeet (filter #(let [u (uri %)] (or (= u "syotettavanarvontyypit_kielikoe") (= u "syotettavanarvontyypit_valintakoe"))) (valintaperuste "valintaperusteDTO"))]
      {(valintaperuste "hakukohdeOid") (map transform-dto hakukohteen-kokeet)}))

  (apply merge (map find-valintaperusteen-kokeet valintaperusteet)))

(defn find-kielikokeet [oppijat]
  (defn- transform-arvosana [arvosana]
    (if (nil? arvosana) {} {:osallistuminen (get (get arvosana "arvio") "arvosana") :kieli (get arvosana "lisatieto")}))

  (defn- arvosanat [suoritus]
    (map transform-arvosana (suoritus "arvosanat")))

  (defn- komo [suoritus]
    (get (get suoritus "suoritus") "komo"))

  (defn- find-oppijan-kielikokeet [oppija]
    (let [oppijan-kielikokeet (filter #(= "ammatillisenKielikoe" (komo %)) (get oppija "suoritukset"))]
      (if (empty? oppijan-kielikokeet) {} {(get oppija "oppijanumero") (flatten (map arvosanat oppijan-kielikokeet))})))

  (let [result (apply merge (map find-oppijan-kielikokeet oppijat))]
    (if (empty? result) {} result)))

(def valintatapajono-trim-keys ["ehdollisenHyvaksymisenEhtoEN" "ehdollisenHyvaksymisenEhtoFI" "ehdollisenHyvaksymisenEhtoKoodi"
                                  "ehdollisenHyvaksymisenEhtoSV" "ehdollisestiHyvaksyttavissa" "eiVarasijatayttoa"
                                  "hakemuksenTilanViimeisinMuutos" "hakeneet" "hyvaksytty" "hyvaksyttyVarasijalta"
                                  "paasyJaSoveltuvuusKokeenTulos" "julkaistavissa" "tayttojono" "valintatapajonoNimi"
                                  "valintatapajonoPrioriteetti" "valintatuloksenViimeisinMuutos" "varalla"
                                  "varasijaTayttoPaivat" "varasijanNumero" "varasijat" "varasijojaKaytetaanAlkaen"
                                  "varasijojaTaytetaanAsti"])

(defn trim-streaming-response [vastaanotot]
  (defn trim-hakutoive [h]
    (let [hakutoive (dissoc h "pistetiedot" "tarjoajaOid" "ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet" "kaikkiJonotSijoiteltu" "hakutoive")
          valintatapajonot (map #(apply dissoc % valintatapajono-trim-keys) (h "hakutoiveenValintatapajonot"))
          hakijaryhmat (map #(dissoc % "kiintio" "nimi" "oid" "valintatapajonoOid") (h "hakijaryhmat"))]
      (assoc hakutoive "hakijaryhmat" hakijaryhmat "hakutoiveenValintatapajonot" valintatapajonot)))

  (defn trim-vastaanotto [v]
    (let [hakutoiveet (map trim-hakutoive (v "hakutoiveet"))
          vastaanotto (dissoc v "etunimi" "sukunimi")]
        (assoc vastaanotto "hakutoiveet" hakutoiveet)))
  (map trim-vastaanotto vastaanotot))

(defn vastaanotot-channel [host haku-oid]
  (log/info (format "Haku %s haetaan vastaanotot..." haku-oid))
  (let [mapper (comp trim-streaming-response parse-json-body-stream)]
    (get-as-channel (format valinta-tulos-service-api host haku-oid) {:as :stream} mapper)))

(defn post-json-with-cas-channel [url data jsession-id mapper]
  (let [options {:as :stream :timeout 200000 :headers {"Content-Type" "application/json; charset=utf-8" "Cookie" (str "JSESSIONID=" jsession-id)}}]
    (post-as-channel url (to-json data) options mapper)))

(defn fetch-kokeet-channel [fetch-jsession-id host haku-oid hakukohde-oidit]
  (log/info (format "Haku %s haetaan valintakokeet %d hakukohteelle..." haku-oid (count hakukohde-oidit)))
  (go-try
    (let [jsession-id (<? (fetch-jsession-id "/valintaperusteet-service"))
          mapper (comp find-valintakokeet parse-json-body-stream)
          valintaperusteet (<? (post-json-with-cas-channel (format valintaperusteet-service-api host) hakukohde-oidit jsession-id mapper))]
      valintaperusteet)))

(defn fetch-valintapisteet-channel [host haku-oid kaikki-hakemus-oidit]
  (log/info (format "Haku %s haetaan valintapisteet %d hakemukselle..." haku-oid (count kaikki-hakemus-oidit)))
  (def url (format valintapiste-service-api host))
  (defn- group-valintapisteet [valintapisteet]
    (apply merge (map (fn [p] {(p "hakemusOID") (p "pisteet")}) valintapisteet)))
  (def mapper (comp group-valintapisteet parse-json-body-stream))
  (defn- fetch-valintapisteet-hakemuksille [hakemus-oidit]
    (post-as-channel url (to-json hakemus-oidit) {:as :stream :timeout 200000 :headers {"Content-Type" "application/json; charset=utf-8"}} mapper))
  (async/map vector (map #(fetch-valintapisteet-hakemuksille %) (partition valintapisteet-batch-size valintapisteet-batch-size nil kaikki-hakemus-oidit))))

(defn fetch-ammatilliset-kielikokeet-channel [fetch-jsession-id host haku-oid kaikki-oppijanumerot]
  (log/info (format "Haku %s haetaan ammatilliset kielikokeet %d oppijalle..." haku-oid (count kaikki-oppijanumerot)))
  (def mapper (comp find-kielikokeet parse-json-body-stream))
  (defn- fetch-ammatilliset-kielikokeet-oppijanumeroille [jsession-id oppijanumerot]
    (post-json-with-cas-channel (format suoritusrekisteri-service-api host haku-oid) oppijanumerot jsession-id mapper))
  (go-try
    (let [jsession-id (<? (fetch-jsession-id "/suoritusrekisteri"))
          partitions (partition oppijat-batch-size oppijat-batch-size nil kaikki-oppijanumerot)
          valintaperusteet (<? (async/map vector (map #(fetch-ammatilliset-kielikokeet-oppijanumeroille jsession-id %) partitions)))]
      (apply merge valintaperusteet))))

(defn vastaanotot-for-haku [config haku-oid request channel]

  (let [vastaanotto-host-virkailija (config :vastaanotto-host-virkailija)
        valintapiste-host-virkailija (config :valintapiste-host-virkailija)
        host-virkailija (config :host-virkailija)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (async/go
      (try
          (let [vastaanotot (<? (vastaanotot-channel vastaanotto-host-virkailija haku-oid))
                hakukohde-oidit (distinct (map #(% "hakukohdeOid") (flatten (map #(% "hakutoiveet") vastaanotot))))
                hakemus-oidit (map #(% "hakemusOid") vastaanotot)
                fetch-jsession-id (jsessionid-fetcher-channel host-virkailija username password)
                valintakokeet (<? (fetch-kokeet-channel fetch-jsession-id host-virkailija haku-oid hakukohde-oidit))
                valintapisteet (<? (fetch-valintapisteet-channel valintapiste-host-virkailija haku-oid hakemus-oidit))
                oppijanumerot (map #(% "hakijaOid") vastaanotot)
                kielikokeet (<? (fetch-ammatilliset-kielikokeet-channel  fetch-jsession-id host-virkailija haku-oid oppijanumerot))]
            (log/info (format "Haku %s hakijoita %d kpl" haku-oid (count hakemus-oidit)))
            (log/info (format "Haku %s hakukohteita %d kpl" haku-oid (count hakukohde-oidit)))
            (log/info (format "Haku %s hakemuksia %d kpl" haku-oid (count hakemus-oidit)))
            (log/info (format "Haku %s valintakokeet %d kpl" haku-oid (count valintakokeet)))
            (log/info (format "Haku %s valintapisteitä %d kpl" haku-oid (count valintapisteet)))
            (log/info (format "Haku %s kielikokeita %d kpl" haku-oid (count kielikokeet)))
            (let [build-vastaanotto (vastaanotto-builder valintakokeet (apply merge valintapisteet) kielikokeet)
                  json (to-json (map build-vastaanotto vastaanotot))]
              (-> channel
                  (status 200)
                  (body-and-close json))))
          (catch Exception e
            (do
              (log/error (format "Virhe haettaessa vastaanottoja haulle %s!" haku-oid), e)
              (exception-response channel)))))))

(defn vastaanotto-resource [config haku-oid request channel]
  (vastaanotot-for-haku config haku-oid request channel)
  (schedule-task (* 1000 60 60 12) (close channel)))