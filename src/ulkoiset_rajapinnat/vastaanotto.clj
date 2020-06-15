(ns ulkoiset-rajapinnat.vastaanotto
  (:require [full.async :refer :all]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [clojure.core.async :as async]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request]]
            [ulkoiset-rajapinnat.utils.tarjonta :refer [hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan haku-for-haku-oid-channel]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [post-json-as-channel get-as-channel status body-and-close exception-response to-json parse-json-body-stream body parse-json-request]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching get-value-if-not-nil]]
            [ulkoiset-rajapinnat.utils.async_safe :refer :all]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clojure.core.cache :as cache]

            )
  (:import (java.time Duration)))

(s/defschema Vastaanotto
  {:henkilo_oid                  s/Str
   (s/optional-key :hakutoiveet) {
                                  (s/optional-key :hakukohde_oid)                              s/Any
                                  (s/optional-key :valinnan_tila)                              s/Any
                                  (s/optional-key :valinnan_tilan_lisatieto)                   s/Any
                                  (s/optional-key :valintatapajono)                            s/Any
                                  (s/optional-key :hakijan_lopullinen_jonosija)                s/Any
                                  (s/optional-key :hakijan_jonosijan_tarkenne)                 s/Any
                                  (s/optional-key :yhteispisteet)                              s/Any
                                  (s/optional-key :ilmoittautumisen_tila)                      s/Any
                                  (s/optional-key :vastaanoton_tila)                           s/Any
                                  (s/optional-key :alin_hyvaksytty_pistemaara)                 s/Any
                                  (s/optional-key :hyvaksytty_harkinnanvaraisesti)             s/Any
                                  (s/optional-key :hyvaksytty_ensikertalaisten_hakijaryhmasta) s/Any
                                  (s/optional-key :osallistui_paasykokeeseen)                  s/Any
                                  (s/optional-key :osallistui_kielikokeeseen)                  s/Any
                                  }})

(def oppijat-batch-size 5000)
(def valintapisteet-batch-size 30000)
(def valinta-tulos-service-timeout-millis (.toMillis (. Duration ofMinutes 30)))

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
      (try
        (let [hakutoive-oid (hakutoive "hakukohdeOid")
                    hakutoiveen-kokeet (get hakutoiveiden-kokeet hakutoive-oid)
                    valintatapajono (or (first (hakutoive "hakutoiveenValintatapajonot")) {})
                    kielikokeiden-tunnisteet (map #(get % :tunniste) (filter #(get % :kielikoe) hakutoiveen-kokeet))
                    valintakokeiden-tunnisteet (map #(get % :tunniste) (filter #(get % :valintakoe) hakutoiveen-kokeet))
                    osallistuminen (osallistuminen-checker hakemuksen-valintapisteet)]

                (def kielikokeeseen-osallistuminen
                  (let [muuKielikoeOsallistuminen (osallistuminen kielikokeiden-tunnisteet)
                        ammatillinenKielikoeOsallistuminen (ammatilliseen-kielikokeeseen-osallistuminen hakijan-kielikokeet)]
                    (if (or muuKielikoeOsallistuminen ammatillinenKielikoeOsallistuminen) true
                                                                                          (if (or (false? muuKielikoeOsallistuminen) (false? ammatillinenKielikoeOsallistuminen)) false nil))))

                {"hakukohde_oid"                              hakutoive-oid
                 "valinnan_tila"                              (valintatapajono "tila")
                 "valinnan_tilan_lisatieto"                   (get-in valintatapajono ["tilanKuvaukset" "FI"])
                 "valintatapajono"                            (valintatapajono "valintatapajonoOid")
                 "hakijan_lopullinen_jonosija"                (valintatapajono "jonosija")
                 "hakijan_jonosijan_tarkenne"                 (valintatapajono "tasasijaJonosija")
                 "yhteispisteet"                              (valintatapajono "pisteet")
                 "ilmoittautumisen_tila"                      (valintatapajono "ilmoittautumisTila")
                 "vastaanoton_tila"                           (hakutoive "vastaanottotieto")
                 "alin_hyvaksytty_pistemaara"                 (valintatapajono "alinHyvaksyttyPistemaara")
                 "hyvaksytty_harkinnanvaraisesti"             (valintatapajono "hyvaksyttyHarkinnanvaraisesti")
                 "hyvaksytty_ensikertalaisten_hakijaryhmasta" (hyvaksytty-ensikertalaisen-hakijaryhmasta (hakutoive "hakijaryhmat"))
                 "osallistui_paasykokeeseen"                  (osallistuminen valintakokeiden-tunnisteet)
                 "osallistui_kielikokeeseen"                  kielikokeeseen-osallistuminen})
        (catch Exception e
          (do
            (log/errorf e "Virhe käsiteltäessä hakutoivetta %s" hakutoive)
            (throw e))))))

  (fn [vastaanotto]
    (try
      (let [hakemus-oid (vastaanotto "hakemusOid")
                hakija-oid (vastaanotto "hakijaOid")
                hakemuksen-pisteet (valintapisteet hakemus-oid)
                hakijan-kielikokeet (kielikokeet hakija-oid)
                build-hakutoive (hakutoive-builder kokeet hakemuksen-pisteet hakijan-kielikokeet)]
            {"henkilo_oid" hakija-oid
             "hakutoiveet" (map build-hakutoive (vastaanotto "hakutoiveet"))})
      (catch Exception e
        (do
          (log/errorf e "Virhe muodostettaessa vastaanottotietoa vastaanotosta %s" vastaanotto)
          (throw e))))))

(defn find-valintakokeet [valintaperusteet]
  (defn- transform-dto [dto]
    (let [tyyppi (dto "syötettavanArvonTyyppi")
          uri (if (nil? tyyppi) nil (tyyppi "uri"))]
      {:tunniste (dto "tunniste") :kielikoe (= uri "syotettavanarvontyypit_kielikoe") :valintakoe (= uri "syotettavanarvontyypit_valintakoe")}))

  (defn- uri [dto]
    (let [tyyppi (dto "syötettavanArvonTyyppi")]
      uri (if (nil? tyyppi) nil (tyyppi "uri"))))

  (defn- find-valintaperusteen-kokeet [valintaperuste]
    (let [hakukohteen-kokeet (filter #(let [u (uri %)] (or (= u "syotettavanarvontyypit_kielikoe") (= u "syotettavanarvontyypit_valintakoe"))) (valintaperuste "valintaperusteDTO"))]
      {(valintaperuste "hakukohdeOid") (map transform-dto hakukohteen-kokeet)}))

  (if (seq valintaperusteet)
    (apply merge (map find-valintaperusteen-kokeet valintaperusteet))
    {}))

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

(defn vastaanotot-whole-haku-channel [haku-oid]
  (log/infof "Haku %s haetaan vastaanotot..." haku-oid)
  (get-as-channel (resolve-url :valinta-tulos-service.internal.streaming-hakemukset haku-oid) {:as :stream :timeout valinta-tulos-service-timeout-millis} parse-json-body-stream))

(defn vastaanotot-of-hakukohdeoids-channel [haku-oid hakukohde-oids]
  (log/infof "Haku %s haetaan vastaanotot %d hakukohteelle valinta-tulos-servicestä..." haku-oid (count hakukohde-oids))
  (post-json-as-channel
    (resolve-url :valinta-tulos-service.internal.streaming-hakemukset haku-oid)
    hakukohde-oids
    parse-json-body-stream))

(defn fetch-kokeet-channel [haku-oid hakukohde-oidit]
  (log/infof "Haku %s haetaan valintakokeet %d hakukohteelle..." haku-oid (count hakukohde-oidit))
  (go-try
    (let [jsession-id (<? (fetch-jsessionid-channel "/valintaperusteet-service"))
          mapper (comp find-valintakokeet parse-json-body-stream)
          valintaperusteet (<? (post-json-as-channel (resolve-url :valintaperusteet-service.hakukohde-avaimet) hakukohde-oidit mapper jsession-id))]
      valintaperusteet)))

(defn fetch-valintapisteet-channel [haku-oid kaikki-hakemus-oidit request user]
  (log/infof "Haku %s haetaan valintapisteet %d hakemukselle..." haku-oid (count kaikki-hakemus-oidit))
  (go-try
    (let [url (resolve-url :valintapiste-service.internal.pisteet-with-hakemusoids "-" (user :personOid) (remote-addr-from-request request) (user-agent-from-request request))
          group-valintapisteet (fn [x] (apply merge (map (fn [p] {(p "hakemusOID") (p "pisteet")}) x)))
          mapper (comp group-valintapisteet parse-json-body-stream)
          post (fn [x] (post-json-as-channel url x mapper))
          partitions (partition valintapisteet-batch-size valintapisteet-batch-size nil kaikki-hakemus-oidit)
          valintapisteet (<? (async-map-safe vector (map #(post %) partitions) []))]
      (apply merge valintapisteet))))

(defn fetch-ammatilliset-kielikokeet-channel [haku-oid kaikki-oppijanumerot]
  (log/infof "Haku %s haetaan ammatilliset kielikokeet %d oppijalle..." haku-oid (count kaikki-oppijanumerot))
  (go-try
    (let [jsession-id (<? (fetch-jsessionid-channel "/suoritusrekisteri"))
          url (resolve-url :suoritusrekisteri-service.oppijat false haku-oid)
          mapper (comp find-kielikokeet parse-json-body-stream)
          post (fn [x] (post-json-as-channel url x mapper jsession-id))
          partitions (partition oppijat-batch-size oppijat-batch-size nil kaikki-oppijanumerot)
          kielikokeet (<? (async-map-safe vector (map #(post %) partitions) []))]
      (apply merge kielikokeet))))

(defn- filter-vastaanotot [haun-hakukohdeoidit haun-vastaanotot haku-oid vuosi kausi]
  (log/info "filter-vastaanotot " haun-hakukohdeoidit haun-vastaanotot)
  (if (not-empty haun-hakukohdeoidit)
    (trim-streaming-response haun-hakukohdeoidit haun-vastaanotot)
    (throw (RuntimeException.
             (format
               "No hakukohde-oids found for haku %s with koulutuksen alkamisvuosi %s and koulutuksen alkamiskausi %s!"
               haku-oid vuosi kausi)))))

(defn- empty-object-channel []
  (async/to-chan '({}) ))

(def two-hours (* 1000 60 60 2))

(defn- update-cache [cache key value]
  (if (cache/has? cache key)
    (do
      (log/info "Cache hit with " key)
      (cache/hit cache key))
    (do
      (log/info "Cache miss with " key)
      (cache/miss cache key value))))

(defn memo [cache f]
  (let [cache (atom cache)]
    (with-meta
      (fn [& params]
        (log/info "params for getter function: " params)
        (async/go
          (when-let [res (or (cache/lookup @cache params)
                             (<? (apply f params)))]
            (swap! cache update-cache params res)
            res)))
      {::cache cache})
    )
  )

(defn- make-memo-fn [memo-factory]
  (fn [f & opts]
    (memo (apply memo-factory {} opts) f)))

(def cached-vastaanotot-for-hakukohdes
  ((make-memo-fn cache/ttl-cache-factory) vastaanotot-of-hakukohdeoids-channel :ttl two-hours))

(defn vastaanotot-for-haku [haku-oid vuosi kausi request user channel log-to-access-log]
  (async/go
    (try
      (if (seq (<? (haku-for-haku-oid-channel haku-oid)))
        (let [haun-hakukohdeoidit-ch (hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan haku-oid vuosi kausi)
              haun-vastaanotot-ch (vastaanotot-whole-haku-channel haku-oid)
              vastaanotot (filter-vastaanotot (<? haun-hakukohdeoidit-ch) (<? haun-vastaanotot-ch) haku-oid vuosi kausi)
              hakukohde-oidit (distinct (map #(% "hakukohdeOid") (flatten (map #(% "hakutoiveet") vastaanotot))))
              hakemus-oidit (map #(% "hakemusOid") vastaanotot)
              valintakokeet-ch (if (empty? hakukohde-oidit) (empty-object-channel) (fetch-kokeet-channel haku-oid hakukohde-oidit))
              valintapisteet-ch (if (empty? hakemus-oidit) (empty-object-channel) (fetch-valintapisteet-channel haku-oid hakemus-oidit request user))
              oppijanumerot (map #(% "hakijaOid") vastaanotot)
              kielikokeet-ch (if (empty? oppijanumerot) (empty-object-channel) (fetch-ammatilliset-kielikokeet-channel haku-oid oppijanumerot))]
          (log/infof "Haku %s hakijoita %d kpl" haku-oid (count hakemus-oidit))
          (log/infof "Haku %s hakukohteita %d kpl" haku-oid (count hakukohde-oidit))
          (log/infof "Haku %s hakemuksia %d kpl" haku-oid (count hakemus-oidit))
          (let [build-vastaanotto (vastaanotto-builder (<? valintakokeet-ch) (<? valintapisteet-ch) (<? kielikokeet-ch))
                json (to-json (pmap build-vastaanotto vastaanotot))]
            (log-to-access-log 200 nil)
            (-> channel
                (status 200)
                (body-and-close json))))
        (let [message (format "Haku %s not found" haku-oid)]
          (status channel 404)
          (body channel (to-json {:error message}))
          (close channel)
          (log-to-access-log 404 message)))
      (catch Exception e
        (do
          (log/errorf e "Virhe haettaessa vastaanottoja haulle %s!" haku-oid)
          (log-to-access-log 500 (.getMessage e))
          ((exception-response channel) e))))))

(defn get-vastaanotot-channel [haku-oid hakukohde-oids]
  (if (= (count hakukohde-oids) 1)
    (do
      (log/info "Getting with/from cache as only 1 hakukohdeOid given")
      (cached-vastaanotot-for-hakukohdes haku-oid hakukohde-oids))
    (do
      (log/info "Getting normally.")
      (vastaanotot-of-hakukohdeoids-channel haku-oid hakukohde-oids)))
  )

(defn vastaanotot-for-haku-and-hakukohdeoids [haku-oid hakukohde-oids request user channel log-to-access-log]
  (log/info "Getting vastaanotot for hakukohtees: " hakukohde-oids)
  (async/go
    (try
      (if (seq (<? (haku-for-haku-oid-channel haku-oid)))
        (let [vastaanotot (<? (get-vastaanotot-channel haku-oid hakukohde-oids))
              ;vastaanotot (<?(cached-vastaanotot-for-hakukohdes haku-oid hakukohde-oids))
              vastaanottojen-hakukohde-oidit (distinct (map #(% "hakukohdeOid") (flatten (map #(% "hakutoiveet") vastaanotot))))
              hakemus-oidit (map #(% "hakemusOid") vastaanotot)
              valintakokeet-ch (if (empty? vastaanottojen-hakukohde-oidit) (empty-object-channel) (fetch-kokeet-channel haku-oid vastaanottojen-hakukohde-oidit))
              valintapisteet-ch (if (empty? hakemus-oidit) (empty-object-channel) (fetch-valintapisteet-channel haku-oid hakemus-oidit request user))
              oppijanumerot (map #(% "hakijaOid") vastaanotot)
              kielikokeet-ch (if (empty? oppijanumerot) (empty-object-channel) (fetch-ammatilliset-kielikokeet-channel haku-oid oppijanumerot))]
          (log/infof "Haku %s hakijoita %d kpl %d hakukohteeseen, joissa tuloksia" haku-oid (count hakemus-oidit) (count vastaanottojen-hakukohde-oidit))
          (log/infof "Haku %s hakukohteita, joissa tuloksia %d kpl" haku-oid (count vastaanottojen-hakukohde-oidit))
          (log/infof "Haku %s hakemuksia %d kpl %d hakukohteeseen, joissa tuloksia" haku-oid (count hakemus-oidit) (count vastaanottojen-hakukohde-oidit))
          (let [build-vastaanotto (vastaanotto-builder (<? valintakokeet-ch) (<? valintapisteet-ch) (<? kielikokeet-ch))
                json (to-json (pmap build-vastaanotto vastaanotot))]
            (log-to-access-log 200 nil)
            (-> channel
                (status 200)
                (body-and-close json))))
        (let [message (format "Haku %s not found" haku-oid)]
          (status channel 404)
          (body channel (to-json {:error message}))
          (close channel)
          (log-to-access-log 404 message)))
      (catch Exception e
        (do
          (log/errorf e "Virhe haettaessa vastaanottoja haun %s %d hakukohteelle!" haku-oid (count hakukohde-oids))
          (log-to-access-log 500 (.getMessage e))
          ((exception-response channel) e))))))

(defn vastaanotto-resource
  ([haku-oid vuosi kausi request user channel log-to-access-log]
   (vastaanotot-for-haku haku-oid vuosi kausi request user channel log-to-access-log)
   (schedule-task (* 1000 60 60 12) (close channel)))
  ([haku-oid request user channel log-to-access-log]
   (vastaanotot-for-haku-and-hakukohdeoids haku-oid (parse-json-request request) request user channel log-to-access-log)
   (schedule-task (* 1000 60 60 12) (close channel))))
