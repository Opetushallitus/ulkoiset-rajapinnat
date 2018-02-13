(ns ulkoiset-rajapinnat.vastaanotto
  (:require [full.async :refer :all]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [clojure.core.async :as async]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request]]
            [ulkoiset-rajapinnat.utils.tarjonta :refer [hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-channel get-as-channel status body-and-close exception-response to-json parse-json-body-stream]]
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
        (if (some #(= "OSALLISTUI" %) osallistumiset) true
          (if (some #(= "EI_OSALLISTUNUT" %) osallistumiset) false nil)))))

  (defn- ammatilliseen-kielikokeeseen-osallistuminen [hakijan-kielikokeet]
    (let [osallistumiset (map #(% :osallistuminen) hakijan-kielikokeet)]
      (if (some #(= "osallistui" %) osallistumiset) true
        (if (some #(= "ei_osallistunut" %) osallistumiset) false nil))))

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
            (if (or muuKielikoeOsallistuminen ammatillinenKielikoeOsallistuminen) true
              (if (or (false? muuKielikoeOsallistuminen) (false? ammatillinenKielikoeOsallistuminen)) false nil))))

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
  (let [arvosana (fn [a] (if (nil? a) {} {:osallistuminen (get (get a "arvio") "arvosana") :kieli (get a "lisatieto")}))
        arvosanat (fn [s] (map arvosana (s "arvosanat")))
        komo (fn [s] (get (get s "suoritus") "komo"))
        kielikokeet (fn [o] (filter #(= "ammatillisenKielikoe" (komo %)) (get o "suoritukset")))
        oppijan-kielikokeet (fn [o] (let [k (kielikokeet o)] (if (empty? k) {} {(get o "oppijanumero") (flatten (map arvosanat k))})))
        oppijoiden-kielikokeet (apply merge (map oppijan-kielikokeet oppijat))]
    (if (empty? oppijoiden-kielikokeet) {} oppijoiden-kielikokeet)))

(def valintatapajono-trim-keys ["ehdollisenHyvaksymisenEhtoEN" "ehdollisenHyvaksymisenEhtoFI" "ehdollisenHyvaksymisenEhtoKoodi"
                                  "ehdollisenHyvaksymisenEhtoSV" "ehdollisestiHyvaksyttavissa" "eiVarasijatayttoa"
                                  "hakemuksenTilanViimeisinMuutos" "hakeneet" "hyvaksytty" "hyvaksyttyVarasijalta"
                                  "paasyJaSoveltuvuusKokeenTulos" "julkaistavissa" "tayttojono" "valintatapajonoNimi"
                                  "valintatapajonoPrioriteetti" "valintatuloksenViimeisinMuutos" "varalla"
                                  "varasijaTayttoPaivat" "varasijanNumero" "varasijat" "varasijojaKaytetaanAlkaen"
                                  "varasijojaTaytetaanAsti"])

(defn trim-streaming-response [hakukohde-oidit vastaanotot]
  (defn- trim-hakutoive [h]
    (let [hakutoive (dissoc h "pistetiedot" "tarjoajaOid" "ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet" "kaikkiJonotSijoiteltu" "hakutoive")
          valintatapajonot (map #(apply dissoc % valintatapajono-trim-keys) (h "hakutoiveenValintatapajonot"))
          hakijaryhmat (map #(dissoc % "kiintio" "nimi" "oid" "valintatapajonoOid") (h "hakijaryhmat"))]
      (assoc hakutoive "hakijaryhmat" hakijaryhmat "hakutoiveenValintatapajonot" valintatapajonot)))

  (defn- trim-vastaanotto [v]
    (let [hakutoive-filter (fn [h] (some #(= % (h "hakukohdeOid")) hakukohde-oidit))
          hakutoiveet (map trim-hakutoive (filter hakutoive-filter (v "hakutoiveet")))
          vastaanotto (dissoc v "etunimi" "sukunimi")]
        (assoc vastaanotto "hakutoiveet" hakutoiveet)))
  (if (empty? hakukohde-oidit) [] (filter #(not-empty (% "hakutoiveet")) (map trim-vastaanotto vastaanotot))))

(defn vastaanotot-channel [haku-oid hakukohde-oidit]
  (log/info (format "Haku %s haetaan vastaanotot..." haku-oid))
  (let [mapper (comp (partial trim-streaming-response hakukohde-oidit) parse-json-body-stream)]
    (get-as-channel (resolve-url :valinta-tulos-service.internal.streaming-hakemukset haku-oid) {:as :stream} mapper)))

(defn fetch-kokeet-channel [haku-oid hakukohde-oidit]
  (log/info (format "Haku %s haetaan valintakokeet %d hakukohteelle..." haku-oid (count hakukohde-oidit)))
  (go-try
    (let [jsession-id (<? (fetch-jsessionid-channel "/valintaperusteet-service"))
          mapper (comp find-valintakokeet parse-json-body-stream)
          valintaperusteet (<? (post-json-as-channel (resolve-url :valintaperusteet-service.hakukohde-avaimet) hakukohde-oidit mapper jsession-id))]
      valintaperusteet)))

(defn fetch-valintapisteet-channel [haku-oid kaikki-hakemus-oidit request user]
  (log/info (format "Haku %s haetaan valintapisteet %d hakemukselle..." haku-oid (count kaikki-hakemus-oidit)))
  (go-try
    (let [url (resolve-url :valintapiste-service.internal.pisteet-with-hakemusoids "-" (user :personOid) (remote-addr-from-request request) (user-agent-from-request request))
          group-valintapisteet (fn [x] (apply merge (map (fn [p] {(p "hakemusOID") (p "pisteet")}) x)))
          mapper (comp group-valintapisteet parse-json-body-stream)
          post (fn [x] (post-json-as-channel url x mapper))
          partitions (partition valintapisteet-batch-size valintapisteet-batch-size nil kaikki-hakemus-oidit)
          valintapisteet (<? (async/map vector (map #(post %) partitions)))]
      (apply merge valintapisteet))))

(defn fetch-ammatilliset-kielikokeet-channel [haku-oid kaikki-oppijanumerot]
  (log/info (format "Haku %s haetaan ammatilliset kielikokeet %d oppijalle..." haku-oid (count kaikki-oppijanumerot)))
  (go-try
    (let [jsession-id (<? (fetch-jsessionid-channel "/suoritusrekisteri"))
          url (resolve-url :suoritusrekisteri-service.oppijat false haku-oid)
          mapper (comp find-kielikokeet parse-json-body-stream)
          post (fn [x] (post-json-as-channel url x mapper jsession-id))
          partitions (partition oppijat-batch-size oppijat-batch-size nil kaikki-oppijanumerot)
          valintaperusteet (<? (async/map vector (map #(post %) partitions)))]
      (apply merge valintaperusteet))))

(defn vastaanotot-for-haku [haku-oid vuosi kausi request user channel]
  (async/go
    (try
      (let [haun-hakukohteet (<? (hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan haku-oid vuosi kausi))
            vastaanotot (<? (vastaanotot-channel haku-oid haun-hakukohteet))
            hakukohde-oidit (distinct (map #(% "hakukohdeOid") (flatten (map #(% "hakutoiveet") vastaanotot))))
            hakemus-oidit (map #(% "hakemusOid") vastaanotot)
            valintakokeet (<? (fetch-kokeet-channel haku-oid hakukohde-oidit))
            valintapisteet (<? (fetch-valintapisteet-channel haku-oid hakemus-oidit request user))
            oppijanumerot (map #(% "hakijaOid") vastaanotot)
            kielikokeet (<? (fetch-ammatilliset-kielikokeet-channel haku-oid oppijanumerot))]
        (log/info (format "Haku %s hakijoita %d kpl" haku-oid (count hakemus-oidit)))
        (log/info (format "Haku %s hakukohteita %d kpl" haku-oid (count hakukohde-oidit)))
        (log/info (format "Haku %s hakemuksia %d kpl" haku-oid (count hakemus-oidit)))
        (log/info (format "Haku %s valintakokeet %d kpl" haku-oid (count valintakokeet)))
        (log/info (format "Haku %s valintapisteitä %d kpl" haku-oid (count valintapisteet)))
        (log/info (format "Haku %s kielikokeita %d kpl" haku-oid (count kielikokeet)))
        (let [build-vastaanotto (vastaanotto-builder valintakokeet valintapisteet kielikokeet)
              json (to-json (map build-vastaanotto vastaanotot))]
          (-> channel
              (status 200)
              (body-and-close json))))
      (catch Exception e
        (do
          (log/error (format "Virhe haettaessa vastaanottoja haulle %s!" haku-oid), e)
          ((exception-response channel) e))))))

(defn vastaanotto-resource [haku-oid vuosi kausi request user channel]
  (vastaanotot-for-haku haku-oid vuosi kausi request user channel)
  (schedule-task (* 1000 60 60 12) (close channel)))