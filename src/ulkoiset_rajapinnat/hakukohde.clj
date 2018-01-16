(ns ulkoiset-rajapinnat.hakukohde
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-in-batch]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-type-and-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [ulkoiset-rajapinnat.utils.snippets :refer [remove-when remove-nils]]
            [manifold.deferred :as d]))

(def hakukohde-api "%s/tarjonta-service/rest/v1/hakukohde/search?hakuOid=%s&tila=JULKAISTU")

(defn log-fetch [resource-name start-time response]
  (log/debug "Fetching '{}' ready with status {}! Took {}ms!" resource-name (response :status) (- (System/currentTimeMillis) start-time))
  response)

(s/defschema Hakukohde
             {:hakukohteen_oid s/Str
              (s/optional-key :organisaatiot) {
                                               (s/optional-key :organisaation_oid) s/Str
                                               (s/optional-key :koulutustoimijan_ytunnus) s/Str
                                               (s/optional-key :oppilaitos_koodi) s/Str
                                               (s/optional-key :organisaation_kuntakoodi) s/Str
                                               (s/optional-key :organisaation_nimi) s/Str }
              (s/optional-key :hakukohteen_nimi) {:fi s/Str :en s/Str :sv s/Str}
              (s/optional-key :koulutuksen_opetuskieli) [s/Str]
              (s/optional-key :hakukohteen_koulutuskoodit) [s/Str]
              (s/optional-key :hakukohteen_koodi) s/Str
              (s/optional-key :pohjakoulutusvaatimus) s/Str
              (s/optional-key :hakijalle_ilmoitetut_aloituspaikat) s/Int
              (s/optional-key :valintojen_aloituspaikat) s/Int
              (s/optional-key :ensikertalaisten_aloituspaikat) s/Str
              })

(defn transform-organisaatio
  [organisaatio-entry]
  (let [organisaatio (first (.getValue organisaatio-entry))]
    {"organisaation_oid" (organisaatio "oid")
     "koulutustoimijan_ytunnus" (organisaatio "ytunnus")
     "oppilaitos_koodi" (organisaatio "oppilaitosKoodi")
     "organisaation_kuntakoodi" (str/replace (organisaatio "kotipaikkaUri") "kunta_" "")
     "organisaation_nimi" (organisaatio "nimi")}))

(defn koulutus-to-koulutuskoodi
  [koulutus]
  (strip-type-and-version-from-tarjonta-koodisto-uri (koulutus "koulutuskoodi")))

(defn transform-hakukohde-tulos [kieli
                                 hakukohde-tulos
                                 organisaatiot
                                 koulutukset
                                 hakukohde]
  (let [org-oids (koulutukset "tarjoajaOids")]
    (merge
      {"organisaatiot" (map transform-organisaatio organisaatiot)
       "hakukohteen_nimi" (hakukohde-tulos "hakukohdeNimi")
       "hakukohteen_koodi" (strip-type-and-version-from-tarjonta-koodisto-uri (hakukohde "koodistoNimi"))
       "hakukohteen_oid"                    (hakukohde-tulos "hakukohdeOid")
       "koulutuksen_opetuskieli"            (map #(kieli %) (hakukohde-tulos "opetuskielet"))
       "hakukohteen_koulutuskoodit" (map #(koulutus-to-koulutuskoodi (first (.getValue %))) koulutukset)
       "pohjakoulutusvaatimus"              (get-in hakukohde ["pohjakoulutusvaatimus" "fi"])
       "hakijalle_ilmoitetut_aloituspaikat" (hakukohde "aloituspaikat")
       "valintojen_aloituspaikat"           (hakukohde "valintojenAloituspaikat")
       "ensikertalaisten_aloituspaikat"     (hakukohde "ensikertalaistenAloituspaikat")})))

(defn result-to-hakukohdes [result]
  (mapcat #(% "tulokset") ((result "result") "tulokset")))

(defn fetch-hakukohde [host-virkailija vuosi]
  (let [promise (get-as-promise (format hakukohde-api host-virkailija vuosi))]
    (chain promise parse-json-body result-to-hakukohdes)))

(defn fetch-komotos-if-kk-haku
  [some-hakukohde]
  (if (= (some-hakukohde "koulutusasteTyyppi") "KORKEAKOULUTUS")
    (d/success! (d/deferred) [])
    (d/success! (d/deferred) [])))

(def haku-api-hakukohde-tulos "%s/tarjonta-service/rest/v1/haku/%s/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1")

(defn fetch-hakukohde-tulos [host-virkailija haku-oid]
  (let [start-time (System/currentTimeMillis)
        promise (get-as-promise (format haku-api-hakukohde-tulos host-virkailija haku-oid))]
    (chain promise (partial log-fetch "hakukohde-tulos" start-time) parse-json-body #(% "tulokset"))))

(def haku-api-koulutus "%s/tarjonta-service/rest/v1/koulutus/search?hakuOid=%s")

(defn- handle-koulutus-result [koulutus-result]
  (mapcat #(get % "tulokset") (get-in koulutus-result ["result" "tulokset"])))

(defn fetch-koulutukset [host-virkailija haku-oid]
  (let [start-time (System/currentTimeMillis)
        promise (get-as-promise (format haku-api-koulutus host-virkailija haku-oid))]
    (chain promise (partial log-fetch "koulutukset" start-time) parse-json-body handle-koulutus-result)))

(defn hakukohde-resource [config haku-oid palauta-null-arvot? request channel]
  (let [host-virkailija (config :host-virkailija)
        remove-tarjonta-data-quirks (partial remove-when #(or (= % "null") (= % "")))]
    (-> (let-flow [kieli (fetch-koodisto host-virkailija "kieli")
                   hakukohde (d/chain (fetch-hakukohde host-virkailija haku-oid) remove-tarjonta-data-quirks)
                   komotos (fetch-komotos-if-kk-haku (first hakukohde))
                   hakukohde-tulos (d/chain (fetch-hakukohde-tulos host-virkailija haku-oid) remove-tarjonta-data-quirks)
                   koulutukset (fetch-koulutukset host-virkailija haku-oid)
                   organisaatiot (fetch-organisations-in-batch config (set (mapcat #(get % "tarjoajat") koulutukset)))]
                  (let [organisaatiot-by-oid (group-by #(% "oid") (flatten organisaatiot))
                        hakukohde-by-oid (group-by #(% "oid") hakukohde)
                        koulutus-by-oid (group-by #(% "oid") koulutukset)
                        hakukohde-converter (partial transform-hakukohde-tulos
                                                     kieli)
                        converted-hakukohdes (map #(let [hk-koulutukset (select-keys koulutus-by-oid (set (% "koulutusOids")))
                                                         hk-organisaatiot (select-keys organisaatiot-by-oid (% "organisaatioOids"))
                                                         hk (first (get hakukohde-by-oid (% "hakukohdeOid")))]
                                                     (hakukohde-converter % hk-organisaatiot hk-koulutukset hk)) hakukohde-tulos)
                        json (to-json (if palauta-null-arvot? converted-hakukohdes (remove-nils converted-hakukohdes)))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))

