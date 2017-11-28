(ns ulkoiset-rajapinnat.hakukohde
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.haku :refer [fetch-koulutukset fetch-hakukohde-tulos]]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-in-batch]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-type-and-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [ulkoiset-rajapinnat.utils.snippets :refer [remove-when remove-nils]]
            [manifold.deferred :as d]))

(def hakukohde-api "%s/tarjonta-service/rest/v1/hakukohde/search?hakuOid=%s&tila=JULKAISTU")

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

