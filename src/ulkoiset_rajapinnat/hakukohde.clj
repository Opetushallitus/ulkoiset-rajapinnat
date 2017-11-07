(ns ulkoiset-rajapinnat.hakukohde
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response response-to-json to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def hakukohde-api "%s/tarjonta-service/rest/v1/hakukohde/search?hakuOid=%s&tila=JULKAISTU")

(defn hakukohde-to-names [kieli hakukohde]
  (let [a (map #(vector (str "hakukohde_nimi." (get kieli (first %))) (last %)) (hakukohde "nimi"))]
    (into (sorted-map) (filter #((comp not str/blank?) (last %)) a))))

(defn transform-hakukohde [kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne hakukohde]
  (merge
    (hakukohde-to-names kieli hakukohde)
    {"hakukohteen_oid" (hakukohde "oid")
     "koulutuksen_opetuskieli" (map #(kieli %) (hakukohde "opetuskielet"))
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
     "pohjakoulutusvaatimus" (get-in hakukohde ["pohjakoulutusvaatimus" "fi"])
     "hakijalle_ilmoitetut_aloituspaikat" (hakukohde "aloituspaikat")
     "valintojen_aloituspaikat" (hakukohde "valintojenAloituspaikat")
     "ensikertalaisten_aloituspaikat" (hakukohde "ensikertalaistenAloituspaikat")}))

(defn result-to-hakukohdes [result]
  (mapcat #(% "tulokset") ((result "result") "tulokset")))

(defn fetch-hakukohde [host-virkailija vuosi]
  (let [promise (get-as-promise (format hakukohde-api host-virkailija vuosi))]
    (chain promise response-to-json result-to-hakukohdes)))

(defn hakukohde-resource [config haku-oid request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (let [host-virkailija (config :host-virkailija)]
                  (-> (let-flow [kieli (fetch-koodisto host-virkailija "kieli")
                                 kausi (fetch-koodisto host-virkailija "kausi")
                                 ;pohjakoulutusvaatimus (fetch-koodisto host-virkailija "pohjakoulutusvaatimustoinenaste")
                                 hakutyyppi (fetch-koodisto host-virkailija "hakutyyppi")
                                 hakutapa (fetch-koodisto host-virkailija "hakutapa")
                                 haunkohdejoukko (fetch-koodisto host-virkailija "haunkohdejoukko")
                                 haunkohdejoukontarkenne (fetch-koodisto host-virkailija "haunkohdejoukontarkenne")
                                 hakukohde (fetch-hakukohde host-virkailija haku-oid)]
                                (let [hakukohde-converter (partial transform-hakukohde kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne)
                                      converted-hakukohdes (map hakukohde-converter hakukohde)
                                      json (to-json converted-hakukohdes)]
                                  (-> channel
                                      (status 200)
                                      (body-and-close json))))
                      (catch Exception (exception-response channel))))
                (schedule-task (* 1000 60 60) (close channel))))

