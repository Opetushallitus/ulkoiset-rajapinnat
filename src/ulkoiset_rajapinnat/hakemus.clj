(ns ulkoiset-rajapinnat.hakemus
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.utils.mongo :as m]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clojure.core.async :as async]))

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
  hakijan_kansalaisuus
  ; missing fields
  lahtokoulun_organisaatio_oid
  lahtokoulun_kuntakoodi
  ensikertalaisuus)

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
    {:hakukohde_oid (get-endswith "-Koulutus-id")
     :harkinnanvarainen_valinta (get-endswith "-discretionary-follow-up")
     :sija (.getKey preferences)}))

(defn hakutoiveet-from-hakemus [document]
  (let [pref-keys-by-sija (collect-preference-keys-by-sija document)]
    {:hakutoiveet (map (partial convert-hakutoive document) pref-keys-by-sija)}))

(defn koulutustausta-from-hakemus [pohjakoulutuskkodw document]
  (let [koulutustausta (get-in document ["answers" "koulutustausta"])
        koulusivistyskieli (remove nil? [(get koulutustausta "lukion_kieli")
                                         (get koulutustausta "perusopetuksen_kieli")])
        pohjakoulutus_2aste (get koulutustausta "POHJAKOULUTUS")
        pohjakoulutus_kk (first (filter #(contains? koulutustausta %) pohjakoulutuskkodw))
        ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa (get koulutustausta "pohjakoulutus_ulk_suoritusmaa")]
    {:hakijan_koulusivistyskieli (first koulusivistyskieli)
     :pohjakoulutus_2aste pohjakoulutus_2aste
     :pohjakoulutus_kk pohjakoulutus_kk
     :ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa ulkomailla_suoritetun_toisen_asteen_tutkinnon_suoritusmaa}))

(defn remove-nils
  [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn convert-hakemus [pohjakoulutuskkodw document]
  (remove-nils (merge
                 (hakutoiveet-from-hakemus document)
                 (koulutustausta-from-hakemus pohjakoulutuskkodw document)
                 {"hakemus_oid" (get document "oid")
                  "henkilo_oid" (get document "personOid")
                  "haku_oid" (get document "applicationSystemId")
                  "hakemus_tila" (get document "state")})))

(defn write-object-to-channel [is-first-written obj channel]
  (let [json (to-json obj)]
    (if (compare-and-set! is-first-written false true)
      (do
        (status channel 200)
        (body channel (str "[" json)))
      (body channel (str "," json)))))

(defn create-hakemus-publisher [mongo-client haku-oid]
  (-> (.getDatabase mongo-client "hakulomake")
      (.getCollection "application")
      (.find (m/and
               (m/in "state" "ACTIVE" "INCOMPLETE")
               (m/eq "applicationSystemId" haku-oid)))))

(defn fetch-hakemukset-for-haku
  [pohjakoulutuskkodw-promise haku-oid mongo-client channel]
  (let [is-first-written (atom false)
        publisher (create-hakemus-publisher mongo-client haku-oid)
        close-channel (fn []
                        (do
                          (if (compare-and-set! is-first-written false true)
                            (do (status channel 200)
                                (body channel "[]")
                                (close channel))
                            (do (body channel "]")
                                (close channel)))))]
    (.subscribe publisher
                (m/subscribe (fn [s]
                             (fn
                               ([]
                                (close-channel))
                               ([document]
                                (if (open? channel)
                                  (let-flow [pohjakoulutuskkodw pohjakoulutuskkodw-promise
                                             hakemus (convert-hakemus pohjakoulutuskkodw document)]
                                            (write-object-to-channel is-first-written hakemus channel))
                                  (do
                                    (log/warn "Client socket no longer listening so closing Mongo-connection!")
                                    (.close s))))
                               ([_ throwable]
                                (log/error "Failed to fetch stuff!" throwable)
                                (write-object-to-channel is-first-written {:error (.getMessage throwable)} channel)
                                (close-channel))))))))

(defn hakemus-resource [config mongo-client haku-oid request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (let [host-virkailija (config :host-virkailija)
                      pohjakoulutuskkodw-promise (chain (fetch-koodisto host-virkailija "pohjakoulutuskkodw") #(vals %))]
                  (fetch-hakemukset-for-haku pohjakoulutuskkodw-promise haku-oid mongo-client channel))
                (schedule-task (* 1000 60 60) (close channel))))
