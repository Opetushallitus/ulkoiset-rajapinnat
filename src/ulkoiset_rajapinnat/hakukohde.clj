(ns ulkoiset-rajapinnat.hakukohde
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.haku :refer [fetch-koulutukset fetch-hakukohde-tulos]]
            [ulkoiset-rajapinnat.organisaatio :refer [fetch-organisations-in-batch]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def hakukohde-api "%s/tarjonta-service/rest/v1/hakukohde/search?hakuOid=%s&tila=JULKAISTU")

(defn hakukohde-to-names [kieli hakukohde]
  (let [a (map #(vector (str "hakukohde_nimi." (get kieli (first %))) (last %)) (hakukohde "nimi"))]
    (into (sorted-map) (filter #((comp not str/blank?) (last %)) a))))

(defn transform-organisaatio
  [organisaatio-entry]
  (let [organisaatio (first (.getValue organisaatio-entry))]
    {"organisaation_oid" (organisaatio "oid")
     "koulutustoimijan_ytunnus" (organisaatio "ytunnus")
     "oppilaitos_koodi" (organisaatio "oppilaitosKoodi")
     "organisaation_kuntakoodi" (str/replace (organisaatio "kotipaikkaUri") "kunta_" "")
     "organisaation_nimi" (organisaatio "nimi")}))

(defn transform-hakukohde-tulos [kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne
                                 hakukohde-tulos
                                 organisaatiot
                                 koulutukset
                                 hakukohde]
  (let [org-oids (koulutukset "tarjoajaOids")]
    ;"hakukohteen_koulutuskoodit" (hakukohde "pohjakoulutusvaatimus") ; Hakukohteeseen liittyvien koulutustusten koulutuskoodit eli ns. kuusinumerokoodit. Voi olla useita per hakukohde. 999999 = tuntematon.
    ;"hakukohteen_koulutukseen_sisaltyvat_koulutuskoodit" (hakukohde "pohjakoulutusvaatimus") ; KK-koulutusten tieto. Jos hakukohde on alempaan ja ylempään tutkintoon, annetaan tässä toiseen liittyvä koodi. OPHn määrittelyyn lisäämä  kenttä. 999999 = tuntematon.
    ;"koulutuksen_koulutustyyppi" (hakukohde "pohjakoulutusvaatimus") ;
    (merge
      {"organisaatiot" (map transform-organisaatio organisaatiot)
       "hakukohteen_nimi" (hakukohde-tulos "hakukohdeNimi")
       "hakukohteen_oid"                    (hakukohde-tulos "hakukohdeOid")
       "koulutuksen_opetuskieli"            (map #(kieli %) (hakukohde-tulos "opetuskielet"))
       ;"hakukohteen_koulutuskoodit" (map #(get (first (.getValue %)) "koulutuskoodi") koulutukset) ; FORMAT! koulutus_381112#6
     ;
     ;"hakukohteen_koodi" (hakukohde "pohjakoulutusvaatimus") ; Kolminumeroinen hakukohteen koodi. 2. asteen tieto.
       "pohjakoulutusvaatimus"              (get-in hakukohde ["pohjakoulutusvaatimus" "fi"])
       "hakijalle_ilmoitetut_aloituspaikat" (hakukohde "aloituspaikat")
       "valintojen_aloituspaikat"           (hakukohde "valintojenAloituspaikat")
       "ensikertalaisten_aloituspaikat"     (hakukohde "ensikertalaistenAloituspaikat")})))

(defn transform-hakukohde [hakukohde-tulos kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne hakukohde]
  (merge
    (hakukohde-to-names kieli hakukohde)
    {"hakukohteen_oid"                    (hakukohde "oid")
     "koulutuksen_opetuskieli"            (map #(kieli %) (hakukohde "opetuskielet"))
     ;
     ;"hakukohteen_koodi" (hakukohde "pohjakoulutusvaatimus") ; Kolminumeroinen hakukohteen koodi. 2. asteen tieto.
     ;"hakukohteen_koulutuskoodit" (hakukohde "pohjakoulutusvaatimus") ; Hakukohteeseen liittyvien koulutustusten koulutuskoodit eli ns. kuusinumerokoodit. Voi olla useita per hakukohde. 999999 = tuntematon.
     ;"hakukohteen_koulutukseen_sisaltyvat_koulutuskoodit" (hakukohde "pohjakoulutusvaatimus") ; KK-koulutusten tieto. Jos hakukohde on alempaan ja ylempään tutkintoon, annetaan tässä toiseen liittyvä koodi. OPHn määrittelyyn lisäämä  kenttä. 999999 = tuntematon.
     ;"koulutuksen_koulutustyyppi" (hakukohde "pohjakoulutusvaatimus") ;
     ;"koulutuksen_organisaation_oid" (hakukohde "pohjakoulutusvaatimus")
     ;"hakukohteen_organisaation_nimi" (hakukohde "pohjakoulutusvaatimus")
     ;"hakukohteen_organisaation_kuntakoodi" (hakukohde "pohjakoulutusvaatimus")
     ;"hakukohteen_koulutustoimijan_ytunnus" (hakukohde "pohjakoulutusvaatimus")
     ;"hakukohteen_oppilaitos_koodi" (hakukohde "pohjakoulutusvaatimus")
     ;
     "pohjakoulutusvaatimus"              (get-in hakukohde ["pohjakoulutusvaatimus" "fi"])
     "hakijalle_ilmoitetut_aloituspaikat" (hakukohde "aloituspaikat")
     "valintojen_aloituspaikat"           (hakukohde "valintojenAloituspaikat")
     "ensikertalaisten_aloituspaikat"     (hakukohde "ensikertalaistenAloituspaikat")}))

(defn result-to-hakukohdes [result]
  (mapcat #(% "tulokset") ((result "result") "tulokset")))

(defn fetch-hakukohde [host-virkailija vuosi]
  (let [promise (get-as-promise (format hakukohde-api host-virkailija vuosi))]
    (chain promise parse-json-body result-to-hakukohdes)))

(defn hakukohde-resource [config haku-oid request channel]
  (let [host-virkailija (config :host-virkailija)]
    (-> (let-flow [kieli (fetch-koodisto host-virkailija "kieli")
                   kausi (fetch-koodisto host-virkailija "kausi")
                   ;pohjakoulutusvaatimus (fetch-koodisto host-virkailija "pohjakoulutusvaatimustoinenaste")
                   hakutyyppi (fetch-koodisto host-virkailija "hakutyyppi")
                   hakutapa (fetch-koodisto host-virkailija "hakutapa")
                   haunkohdejoukko (fetch-koodisto host-virkailija "haunkohdejoukko")
                   haunkohdejoukontarkenne (fetch-koodisto host-virkailija "haunkohdejoukontarkenne")
                   hakukohde-tulos (fetch-hakukohde-tulos host-virkailija haku-oid)
                   hakukohde (fetch-hakukohde host-virkailija haku-oid)
                   koulutukset (fetch-koulutukset host-virkailija haku-oid)
                   organisaatiot (fetch-organisations-in-batch config (set (mapcat #(get % "tarjoajat") koulutukset)))
                   ]
                  (let [organisaatiot-by-oid (group-by #(% "oid") (flatten organisaatiot))
                        hakukohde-by-oid (group-by #(% "oid") hakukohde)
                        koulutus-by-oid (group-by #(% "oid") koulutukset)
                        hakukohde-converter (partial transform-hakukohde-tulos
                                                     kieli
                                                     kausi
                                                     hakutyyppi
                                                     hakutapa
                                                     haunkohdejoukko
                                                     haunkohdejoukontarkenne)
                        converted-hakukohdes (map #(let [hk-koulutukset (select-keys koulutus-by-oid (set (% "koulutusOids")))
                                                         hk-organisaatiot (select-keys organisaatiot-by-oid (% "organisaatioOids"))
                                                         hk (first (get hakukohde-by-oid (% "hakukohdeOid")))]
                                                     (hakukohde-converter % hk-organisaatiot hk-koulutukset hk)) hakukohde-tulos)
                        json (to-json converted-hakukohdes)]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))

