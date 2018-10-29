(ns ulkoiset-rajapinnat.hakemus
  (:require [clojure.string :as str]
            [clojure.core.async :refer [<! close! go go-loop chan timeout >! alt! alts! promise-chan]]
            [clojure.tools.logging :as log]
            [full.async :refer [<? engulf alts? go-try]]
            [schema.core :as s]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-in-batch-channel]]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.tarjonta :refer :all]
            [ulkoiset-rajapinnat.utils.haku_app :refer :all]
            [ulkoiset-rajapinnat.oppija :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [status body body-and-close exception-response to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching get-value-if-not-nil]]
            [org.httpkit.server :refer :all]
            [ulkoiset-rajapinnat.utils.ataru :refer [fetch-hakemukset-from-ataru]]
            [ulkoiset-rajapinnat.utils.snippets :refer [remove-nils]]
            [org.httpkit.timer :refer :all]
            [clojure.core.async.impl.protocols :as impl]
            [clj-time.core :as t]
            [clj-time.format :as f])
  (:refer-clojure :rename {merge core-merge
                           loop  core-loop}))

(def size-of-henkilo-batch-from-onr-at-once 75)

(s/defschema Hakemus
  {:haku_oid                                                                   s/Str
   :hakemus_oid                                                                s/Str
   :henkilo_oid                                                                s/Str
   (s/optional-key :ensikertalaisuus)                                          s/Bool
   (s/optional-key :yksiloity)                                                 s/Str
   (s/optional-key :henkilotunnus)                                             s/Str
   (s/optional-key :syntyma_aika)                                              s/Str
   (s/optional-key :etunimet)                                                  s/Str
   (s/optional-key :sukunimi)                                                  s/Str
   (s/optional-key :sukupuoli_koodi)                                           s/Str
   (s/optional-key :aidinkieli)                                                s/Str

   (s/optional-key :hakijan_asuinmaa)                                          s/Str
   (s/optional-key :hakijan_kotikunta)                                         s/Str
   (s/optional-key :hakijan_kansalaisuus)                                      s/Str

   (s/optional-key :hakijan_koulusivistyskieli)                                s/Str
   (s/optional-key :pohjakoulutus_2aste)                                       s/Str
   (s/optional-key :pohjakoulutus_kk)                                          [s/Str]
   (s/optional-key :lahtokoulun_organisaatio_oid)                              s/Str
   (s/optional-key :ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa) s/Str

   :hakutoiveet                                                                {
                                                                                (s/optional-key :hakukohde_oid)             s/Str
                                                                                (s/optional-key :harkinnanvarainen_valinta) s/Str
                                                                                (s/optional-key :sija)                      s/Str
                                                                                }})

(defn collect-preference-keys-by-sija [document]
  (let [ht (get-in document ["answers" "hakutoiveet"])
        preference-keys (filter not-empty (map #(re-matches #"preference(\d)-(.*)" %) (keys ht)))]
    (group-by second preference-keys)))

(defn find-ends-with [s endswith]
  (first (filter #(str/ends-with? % endswith) s)))

(defn convert-hakutoive [document preferences]
  (let [hakutoiveet (get-in document ["answers" "hakutoiveet"])
        pref-keys (set (map first (.getValue preferences)))
        get-endswith #(get hakutoiveet (find-ends-with pref-keys %))]
    {:hakukohde_oid             (get-endswith "-Koulutus-id")
     :harkinnanvarainen_valinta (get-endswith "-discretionary-follow-up")
     :sija                      (.getKey preferences)}))

(defn oppija-data-from-henkilo [henkilo-opt]
  (if-let [henkilo (first henkilo-opt)]
    {:yksiloity       (get henkilo "yksiloity")
     :henkilotunnus   (get henkilo "hetu")
     :syntyma_aika    (get henkilo "syntymaaika")
     :etunimet        (get henkilo "etunimet")
     :sukunimi        (get henkilo "sukunimi")
     :sukupuoli_koodi (get henkilo "sukupuoli")
     :aidinkieli      (get henkilo "aidinkieli")}
    {}))

(defn hakutoiveet-from-hakemus [document]
  (let [pref-keys-by-sija (collect-preference-keys-by-sija document)]
    {:hakutoiveet (map (partial convert-hakutoive document) pref-keys-by-sija)}))

(defn henkilotiedot-from-hakemus [document]
  (let [henkilotiedot (get-in document ["answers" "henkilotiedot"])]
    {:hakijan_asuinmaa     (get henkilotiedot "asuinmaa")
     :hakijan_kotikunta    (get henkilotiedot "kotikunta")
     :hakijan_kansalaisuus (get henkilotiedot "kansalaisuus")}))

(defn koulutustausta-from-hakemus [pohjakoulutus-koodit document]
  (let [koulutustausta (get-in document ["answers" "koulutustausta"])
        koulusivistyskieli (remove nil? [(get koulutustausta "lukion_kieli")
                                         (get koulutustausta "perusopetuksen_kieli")])
        lahtokoulun_organisaatio_oid (get koulutustausta "lahtokoulu")
        pohjakoulutus_2aste (get koulutustausta "POHJAKOULUTUS")
        pohjakoulutus_kk (filter #(contains? koulutustausta %) (vals pohjakoulutus-koodit))
        ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa (get koulutustausta "pohjakoulutus_ulk_suoritusmaa")]
    {:hakijan_koulusivistyskieli                                (first koulusivistyskieli)
     :pohjakoulutus_2aste                                       pohjakoulutus_2aste
     :pohjakoulutus_kk                                          (if (seq pohjakoulutus_kk) pohjakoulutus_kk nil)
     :lahtokoulun_organisaatio_oid                              lahtokoulun_organisaatio_oid
     :ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa}))

(defn koulutustausta-from-oppija [oppija organisaatiot]
  (defn- parse-loppuPaiva [o] (f/parse opiskelu-date-formatter (get o "loppuPaiva")))
  (defn- parse-valmistuminen [s] (f/parse valmistuminen-date-formatter (get s "valmistuminen")))
  (defn- find-latest [coll parser] (last (sort #(compare (parser %1) (parser %2)) coll)))
  (defn- find-latest-opiskelu [opiskelut] (find-latest opiskelut parse-loppuPaiva))
  (defn- find-latest-suoritus [suoritukset] (find-latest suoritukset parse-valmistuminen))

  (let [opiskelut (flatten (map #(get % "opiskelu") oppija))
        suoritukset (map #(get % "suoritus") (flatten (map #(get % "suoritukset") oppija)))
        latest-opiskelu (find-latest-opiskelu opiskelut)
        luokka (get latest-opiskelu "luokka")
        oppilaitos (get latest-opiskelu "oppilaitosOid")
        latest-suoritus (find-latest-suoritus (filter #(= oppilaitos (get % "myontaja")) suoritukset))
        paattovuosi (if latest-suoritus (t/year (parse-valmistuminen latest-suoritus)) nil)
        opetuskieli (if latest-suoritus (get latest-suoritus "suoritusKieli") nil)
        oppilaitoskoodi (get-value-if-not-nil "oppilaitosKoodi" (find-first-matching "oid" oppilaitos organisaatiot))]
    {:paattoluokka luokka
     :perusopetuksen_paattovuosi paattovuosi
     :perusopetuksen_opetuskieli opetuskieli
     :lahtokoulun_oppilaitos_koodi oppilaitoskoodi}))

(defn convert-ataru-hakemus [pohjakoulutus-koodit palauta-null-arvot? henkilo oppija hakemus]
  (let [data (core-merge
               (oppija-data-from-henkilo henkilo)
               {:hakemus_oid      (get hakemus "hakemus_oid")
                :henkilo_oid      (get hakemus "henkilo_oid")
                :haku_oid         (get hakemus "haku_oid")
                :ensikertalaisuus (if-let [o (first oppija)] (get o "ensikertalainen") nil)
                :hakutoiveet      (map (fn [oid] {:hakukohde_oid oid}) (get hakemus "hakukohde_oids"))})]
    (if palauta-null-arvot?
      data
      (remove-nils data))))

(defn convert-hakemus [pohjakoulutus-koodit palauta-null-arvot? henkilo oppija document is-toisen-asteen-haku? organisaatiot]
  (let [data (core-merge
               (hakutoiveet-from-hakemus document)
               (oppija-data-from-henkilo henkilo)
               (henkilotiedot-from-hakemus document)
               (koulutustausta-from-hakemus pohjakoulutus-koodit document)
               (if is-toisen-asteen-haku? (koulutustausta-from-oppija oppija organisaatiot) {})
               {:hakemus_oid      (get document "oid")
                :henkilo_oid      (get document "personOid")
                :haku_oid         (get document "applicationSystemId")
                :ensikertalaisuus (if-let [o (first oppija)] (get o "ensikertalainen") nil)
                :hakemus_tila     (get document "state")})]
    (if palauta-null-arvot?
      data
      (remove-nils data))))

(defn write-object-to-channel [is-first-written obj channel]
  (if (open? channel)
    (let [json (to-json obj)]
      (if (compare-and-set! is-first-written false true)
        (do
          (status channel 200)
          (body channel (str "[" json)))
        (body channel (str "," json))))
    (throw (RuntimeException. "Client closed channel so no point writing!"))))

(defn drain! [atom]
  (core-loop [oldval @atom]
    (if (compare-and-set! atom oldval [])
      oldval
      (recur @atom))))

(defn document-batch-to-henkilo-oid-list
  [batch]
  (map #(get % "henkilo_oid") batch))


(defn haku-app-adapter [pohjakoulutus-koodit palauta-null-arvot?]
  (fn [batch] [(map #(get % "personOid") batch)
               batch
               (fn [henkilo-by-oid oppijat-by-oid hakemus is-toisen-asteen-haku? organisaatiot]
                 (convert-hakemus
                   pohjakoulutus-koodit
                   palauta-null-arvot?
                   (get henkilo-by-oid (get hakemus "personOid"))
                   (get oppijat-by-oid (get hakemus "personOid"))
                   hakemus
                   is-toisen-asteen-haku?
                   organisaatiot))]))

(defn ataru-adapter [pohjakoulutus-koodit palauta-null-arvot?]
  (fn [batch] [(document-batch-to-henkilo-oid-list batch)
               batch
               (fn [henkilo-by-oid oppijat-by-oid hakemus _ _]
                 (convert-ataru-hakemus
                   pohjakoulutus-koodit
                   palauta-null-arvot?
                   (get henkilo-by-oid (get hakemus "henkilo_oid"))
                   (get oppijat-by-oid (get hakemus "henkilo_oid")) hakemus))]))

(defn- close-and-drain! [channel]
  (go
    (close! channel)
    (engulf channel)))

(defn fetch-hakemukset-for-haku
  [haku-oid vuosi kausi palauta-null-arvot? channel log-to-access-log]
   (go
     (try
       (let [haku (<? (haku-for-haku-oid-channel haku-oid))]
         (if (seq haku)
           (let [start-time (System/currentTimeMillis)
                 counter (atom 0)
                 is-first-written (atom false)
                 hakukohde-oids-for-hakukausi (<? (hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan haku-oid vuosi kausi haku))
                 pohjakoulutus-koodit (<? (koodisto-as-channel "pohjakoulutuskklomake"))
                 is-haku-with-ensikertalaisuus? (is-haku-with-ensikertalaisuus haku)
                 is-toisen-asteen-haku? (is-toinen-aste haku)
                 ataru-channel (fetch-hakemukset-from-ataru haku-oid size-of-henkilo-batch-from-onr-at-once
                                                            (ataru-adapter pohjakoulutus-koodit palauta-null-arvot?))
                 haku-app-channel (if (empty? hakukohde-oids-for-hakukausi)
                                    (go [])
                                    (fetch-hakemukset-from-haku-app-as-streaming-channel
                                      haku-oid hakukohde-oids-for-hakukausi size-of-henkilo-batch-from-onr-at-once
                                      (haku-app-adapter pohjakoulutus-koodit palauta-null-arvot?)))
                 close-channel (fn []
                                 (do
                                   (close-and-drain! haku-app-channel)
                                   (close-and-drain! ataru-channel)
                                   (if (compare-and-set! is-first-written false true)
                                     (do (status channel 200)
                                         (body channel "[]")
                                         (close channel))
                                     (do (body channel "]")
                                         (close channel)))))
                 oppija-service-ticket-channel (fetch-hakurekisteri-service-ticket-channel)]
             (try
               (core-loop [channels [ataru-channel haku-app-channel]]
                 (let [[v ch] (alts? channels)]
                   (if (not (vector? v))
                     (let [new-channels (remove #{ch} channels)]
                       (if (not (empty? new-channels))
                         (recur new-channels)))
                     (let [[henkilo-oids batch mapper] v
                           oppijat (if (or is-haku-with-ensikertalaisuus? is-toisen-asteen-haku?)
                                     (<? (fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel haku-oid henkilo-oids is-haku-with-ensikertalaisuus? oppija-service-ticket-channel)) nil)
                           jsessionid (<? (onr-sessionid-channel))
                           henkilot (<? (fetch-henkilot-channel jsessionid henkilo-oids))
                           oppijat-by-oid (group-by #(get % "oppijanumero") oppijat)
                           henkilo-by-oid (group-by #(get % "oidHenkilo") henkilot)
                           organisaatiot (if is-toisen-asteen-haku? (let [oppilaitos-oids (flatten (map #(get % "oppilaitosOid") (flatten (map #(get % "opiskelu") oppijat))))]
                                                                      (<? (fetch-organisations-in-batch-channel oppilaitos-oids))) nil)]
                       (doseq [hakemus batch]
                         (write-object-to-channel
                           is-first-written
                           (mapper henkilo-by-oid oppijat-by-oid hakemus is-toisen-asteen-haku? organisaatiot)
                           channel))
                       (let [bs (int (count batch))]
                         (swap! counter (partial + bs)))
                       (recur channels)))))
               (log-to-access-log 200 nil)
               (log/info "Returned successfully" @counter "'hakemusta' from Haku-App and Ataru! Took" (- (System/currentTimeMillis) start-time) "ms!")
               (catch Throwable e
                 (do
                   (log/error "Failed to write 'hakemukset'!" e)
                   (write-object-to-channel is-first-written
                                            {:error (.getMessage e)}
                                            channel)
                   (log-to-access-log 500 (.getMessage e))))
               (finally
                 (close-channel))))
           (let [message (format "Haku %s not found" haku-oid)]
             (status channel 404)
             (body channel (to-json {:error message}))
             (close channel)
             (log-to-access-log 404 message))))
     (catch Throwable e (let [error-message (.getMessage e)
                              is-illegal-argument (or (instance? IllegalArgumentException e)
                                                      (instance? IllegalArgumentException (.getCause e)))
                              status-code (if is-illegal-argument 400 500)]
                          (log/error "Exception in fetch-hakemukset-for-haku" e)
                          (status channel status-code)
                          (body channel (to-json {:error error-message}))
                          (close channel)
                          (log-to-access-log status-code error-message))))))

(defn hakemus-resource [haku-oid vuosi kausi palauta-null-arvot? request user channel log-to-access-log]
  (fetch-hakemukset-for-haku haku-oid vuosi kausi palauta-null-arvot? channel log-to-access-log)
  (schedule-task (* 1000 60 60 12) (close channel)))
