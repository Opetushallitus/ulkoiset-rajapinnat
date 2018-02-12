(ns ulkoiset-rajapinnat.hakukohde
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.tarjonta :refer [fetch-tilastoskeskus-hakukohde-channel]]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-in-batch-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [post-as-channel get-as-channel status body body-and-close exception-response parse-json-body-stream to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel strip-type-and-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [full.async :refer :all]
            [clojure.core.async :as async]
            [ulkoiset-rajapinnat.utils.snippets :refer [remove-when remove-nils]]))

(defn log-fetch [resource-name start-time response]
  (log/debug "Fetching '{}' ready with status {}! Took {}ms!" resource-name (response :status) (- (System/currentTimeMillis) start-time))
  response)

(s/defschema Hakukohde
  {:hakukohteen_oid                                                     s/Str
   (s/optional-key :organisaatiot)                                      {
                                                                         (s/optional-key :organisaation_oid)        s/Str
                                                                         (s/optional-key :koulutustoimijan_ytunnus) s/Str
                                                                         (s/optional-key :oppilaitos_koodi)         s/Str
                                                                         (s/optional-key :organisaation_kuntakoodi) s/Str
                                                                         (s/optional-key :organisaation_nimi)       s/Str}
   (s/optional-key :hakukohteen_nimi)                                   {:fi s/Str :en s/Str :sv s/Str}
   (s/optional-key :koulutuksen_opetuskieli)                            [s/Str]
   (s/optional-key :koulutuksen_koulutustyyppi)                         s/Str
   (s/optional-key :hakukohteen_koulutuskoodit)                         [s/Str]
   (s/optional-key :hakukohteen_koulutukseen_sisaltyvat_koulutuskoodit) [s/Str]
   (s/optional-key :hakukohteen_koodi)                                  s/Str
   (s/optional-key :pohjakoulutusvaatimus)                              s/Str
   (s/optional-key :hakijalle_ilmoitetut_aloituspaikat)                 s/Int
   (s/optional-key :valintojen_aloituspaikat)                           s/Int
   (s/optional-key :ensikertalaisten_aloituspaikat)                     s/Str
   })

(defn transform-organisaatio
  [organisaatio-entry]
  (let [organisaatio (first (.getValue organisaatio-entry))]
    {"organisaation_oid"        (organisaatio "oid")
     "koulutustoimijan_ytunnus" (organisaatio "ytunnus")
     "oppilaitos_koodi"         (organisaatio "oppilaitosKoodi")
     "organisaation_kuntakoodi" (str/replace (organisaatio "kotipaikkaUri") "kunta_" "")
     "organisaation_nimi"       (organisaatio "nimi")}))

(defn transform-hakukohde-tulos [kieli
                                 koulutustyyppi
                                 hakukohde-tulos
                                 sisaltyvat-koulutuskoodit
                                 organisaatiot
                                 koulutukset
                                 hakukohde]
  (merge
    {"organisaatiot"                                      (map transform-organisaatio organisaatiot)
     "hakukohteen_nimi"                                   (hakukohde-tulos "hakukohdeNimi")
     "hakukohteen_koodi"                                  (strip-type-and-version-from-tarjonta-koodisto-uri (hakukohde "koodistoNimi"))
     "hakukohteen_oid"                                    (hakukohde-tulos "hakukohdeOid")
     "koulutuksen_koulutustyyppi"                         (if-let [k (first koulutukset)] (if-let [t ((first (second k)) "koulutustyyppiUri")] (koulutustyyppi t)))
     "koulutuksen_opetuskieli"                            (map #(kieli %) (hakukohde-tulos "opetuskielet"))
     "hakukohteen_koulutukseen_sisaltyvat_koulutuskoodit" (seq sisaltyvat-koulutuskoodit)
     "pohjakoulutusvaatimus"                              (get-in hakukohde ["pohjakoulutusvaatimus" "fi"])
     "hakijalle_ilmoitetut_aloituspaikat"                 (hakukohde "aloituspaikat")
     "valintojen_aloituspaikat"                           (hakukohde "valintojenAloituspaikat")
     "ensikertalaisten_aloituspaikat"                     (hakukohde "ensikertalaistenAloituspaikat")}))

(defn result-to-hakukohdes [result]
  (mapcat #(% "tulokset") ((result "result") "tulokset")))

(defn remove-tarjonta-data-quirks [result] (remove-when #(or (= % "null") (= % "")) result))

(defn fetch-hakukohde-channel [haku-oid]
   (let [start-time (System/currentTimeMillis)
         log (partial log-fetch "hakukohde-tulos" start-time)
         mapper (comp remove-tarjonta-data-quirks result-to-hakukohdes parse-json-body-stream log)]
     (get-as-channel (resolve-url :tarjonta-service.hakukohde-search-by-haku-oid haku-oid) {:as :stream} mapper)))

(defn fetch-hakukohde-tulos-channel [haku-oid]
   (let [start-time (System/currentTimeMillis)
         log (partial log-fetch "hakukohde-tulos" start-time)
         mapper (comp remove-tarjonta-data-quirks #(% "tulokset") parse-json-body-stream log)]
     (get-as-channel (resolve-url :tarjonta-service.haku-hakukohde-tulos haku-oid) {:as :stream} mapper)))

(defn- handle-koulutus-result [koulutus-result]
  (mapcat #(get % "tulokset") (get-in koulutus-result ["result" "tulokset"])))

(defn fetch-koulutukset-channel [haku-oid]
  (let [start-time (System/currentTimeMillis)
        mapper (comp handle-koulutus-result parse-json-body-stream (partial log-fetch "koulutukset" start-time))]
    (get-as-channel (resolve-url :tarjonta-service.koulutus-search-by-haku-oid haku-oid) {:as :stream} mapper)))

(defn hakukohde-resource [haku-oid palauta-null-arvot? request user channel]
  (let [hakukohde-tulos-promise (fetch-hakukohde-tulos-channel haku-oid)
        kieli-promise (koodisto-as-channel "kieli")
        koulutustyyppi-promise (koodisto-as-channel "koulutustyyppi")
        hakukohde-promise (fetch-hakukohde-channel haku-oid)
        koulutukset-promise (fetch-koulutukset-channel haku-oid)
        ]
    (async/go
      (try
        (let [hakukohde-tulos (<? hakukohde-tulos-promise)
              all-hakukohde-oids (map #(get % "hakukohdeOid") hakukohde-tulos)
              all-organisaatio-oids (set (mapcat #(get % "organisaatioOids") hakukohde-tulos))
              sisaltyvat-koulutukset-promise (fetch-tilastoskeskus-hakukohde-channel all-hakukohde-oids)
              organisaatiot-promise (fetch-organisations-in-batch-channel all-organisaatio-oids)
              kieli (<? kieli-promise)
              koulutustyyppi (<? koulutustyyppi-promise)
              hakukohde (<? hakukohde-promise)
              koulutukset (<? koulutukset-promise)
              sisaltyvat-koulutukset (<? sisaltyvat-koulutukset-promise)
              organisaatiot (<? organisaatiot-promise)]
          (let [organisaatiot-by-oid (group-by #(% "oid") (flatten organisaatiot))
                hakukohde-by-oid (group-by #(% "oid") hakukohde)
                koulutus-by-oid (group-by #(% "oid") koulutukset)
                sisaltyvat-koulutukset-by-oid (apply merge (map (fn [k] {(get k "hakukohdeOid") (map #(get % "koulutuskoodi") (get k "koulutusLaajuusarvos"))}) sisaltyvat-koulutukset))
                hakukohde-converter (partial transform-hakukohde-tulos
                                             kieli
                                             koulutustyyppi)
                converted-hakukohdes (map #(let [hk-koulutukset (select-keys koulutus-by-oid (set (% "koulutusOids")))
                                                 hk-organisaatiot (select-keys organisaatiot-by-oid (% "organisaatioOids"))
                                                 hk (first (get hakukohde-by-oid (% "hakukohdeOid")))
                                                 sisaltyvat-koulutuskoodit (set (get sisaltyvat-koulutukset-by-oid (% "hakukohdeOid")))]
                                             (hakukohde-converter % sisaltyvat-koulutuskoodit hk-organisaatiot hk-koulutukset hk)) hakukohde-tulos)
                json (to-json (if palauta-null-arvot? converted-hakukohdes (remove-nils converted-hakukohdes)))]
            (-> channel
                (status 200)
                (body-and-close json))))
        (catch Exception e ((exception-response channel) e)))))
  (schedule-task (* 1000 60 60) (close channel)))