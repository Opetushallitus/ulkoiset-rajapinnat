(ns ulkoiset-rajapinnat.hakukohde
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-in-batch-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel status body body-and-close exception-response parse-json-body-stream to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel strip-type-and-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [full.async :refer :all]
            [clojure.core.async :as async]
            [ulkoiset-rajapinnat.utils.snippets :refer [remove-when remove-nils]]))

(def hakukohde-api "%s/tarjonta-service/rest/v1/hakukohde/search?hakuOid=%s&tila=JULKAISTU")
(def haku-api-hakukohde-tulos "%s/tarjonta-service/rest/v1/haku/%s/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1")
(def haku-api-koulutus "%s/tarjonta-service/rest/v1/koulutus/search?hakuOid=%s")

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
                                 koulutustyyppi
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
       "koulutuksen_koulutustyyppi" (if-let [k (first koulutukset)] (if-let [t ((first (second k)) "koulutustyyppiUri")] (koulutustyyppi t)))
       "koulutuksen_opetuskieli"            (map #(kieli %) (hakukohde-tulos "opetuskielet"))
       "hakukohteen_koulutuskoodit" (map #(koulutus-to-koulutuskoodi (first (.getValue %))) koulutukset)
       "pohjakoulutusvaatimus"              (get-in hakukohde ["pohjakoulutusvaatimus" "fi"])
       "hakijalle_ilmoitetut_aloituspaikat" (hakukohde "aloituspaikat")
       "valintojen_aloituspaikat"           (hakukohde "valintojenAloituspaikat")
       "ensikertalaisten_aloituspaikat"     (hakukohde "ensikertalaistenAloituspaikat")})))

(defn result-to-hakukohdes [result]
  (mapcat #(% "tulokset") ((result "result") "tulokset")))

(defn remove-tarjonta-data-quirks [result] (remove-when #(or (= % "null") (= % "")) result))

(defn fetch-hakukohde-channel [host-virkailija haku-oid]
   (let [start-time (System/currentTimeMillis)
         log (partial log-fetch "hakukohde-tulos" start-time)
         mapper (comp remove-tarjonta-data-quirks result-to-hakukohdes parse-json-body-stream log)]
     (get-as-channel (format hakukohde-api host-virkailija haku-oid) {:as :stream} mapper)))

(defn fetch-hakukohde-tulos-channel [host-virkailija haku-oid]
   (let [start-time (System/currentTimeMillis)
         log (partial log-fetch "hakukohde-tulos" start-time)
         mapper (comp remove-tarjonta-data-quirks #(% "tulokset") parse-json-body-stream log)]
     (get-as-channel (format haku-api-hakukohde-tulos host-virkailija haku-oid) {:as :stream} mapper)))

(defn- handle-koulutus-result [koulutus-result]
  (mapcat #(get % "tulokset") (get-in koulutus-result ["result" "tulokset"])))

(defn fetch-koulutukset-channel [host-virkailija haku-oid]
   (let [start-time (System/currentTimeMillis)
         mapper (comp handle-koulutus-result parse-json-body-stream (partial log-fetch "koulutukset" start-time))]
     (get-as-channel (format haku-api-koulutus host-virkailija haku-oid) {:as :stream} mapper)))

(defn hakukohde-resource [config haku-oid palauta-null-arvot? request channel]
  (let [host-virkailija (config :host-virkailija)]
    (async/go
      (try
        (let [kieli (<? (koodisto-as-channel config "kieli"))
              koulutustyyppi (<? (koodisto-as-channel config "koulutustyyppi"))
              hakukohde (<? (fetch-hakukohde-channel host-virkailija haku-oid))
              hakukohde-tulos (<? (fetch-hakukohde-tulos-channel host-virkailija haku-oid))
              koulutukset (<? (fetch-koulutukset-channel host-virkailija haku-oid))
              organisaatiot (<? (fetch-organisations-in-batch-channel config (set (mapcat #(get % "tarjoajat") koulutukset))))]
          (let [organisaatiot-by-oid (group-by #(% "oid") (flatten organisaatiot))
                hakukohde-by-oid (group-by #(% "oid") hakukohde)
                koulutus-by-oid (group-by #(% "oid") koulutukset)
                hakukohde-converter (partial transform-hakukohde-tulos
                                             kieli
                                             koulutustyyppi)
                converted-hakukohdes (map #(let [hk-koulutukset (select-keys koulutus-by-oid (set (% "koulutusOids")))
                                                 hk-organisaatiot (select-keys organisaatiot-by-oid (% "organisaatioOids"))
                                                 hk (first (get hakukohde-by-oid (% "hakukohdeOid")))]
                                             (hakukohde-converter % hk-organisaatiot hk-koulutukset hk)) hakukohde-tulos)
                json (to-json (if palauta-null-arvot? converted-hakukohdes (remove-nils converted-hakukohdes)))]
            (-> channel
                (status 200)
                (body-and-close json))))
        (catch Exception e ((exception-response channel) e)))))
  (schedule-task (* 1000 60 60) (close channel)))