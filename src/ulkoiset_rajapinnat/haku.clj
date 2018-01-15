(ns ulkoiset-rajapinnat.haku
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(s/defschema Haku
             {:haku_oid s/Str
              (s/optional-key :haku_nimi) {:FI s/Str :EN s/Str :SV s/Str}
              (s/optional-key :haun_hakukohteiden_oidit) [s/Str]
              (s/optional-key :hakuvuosi) s/Int
              (s/optional-key :hakukausi) s/Str
              (s/optional-key :koulutuksen_alkamisvuosi) s/Int
              (s/optional-key :koulutuksen_alkamiskausi) s/Str
              (s/optional-key :hakutyyppi_koodi) s/Str
              (s/optional-key :hakutapa_koodi) s/Str
              (s/optional-key :hakukohteiden_priorisointi) s/Bool
              (s/optional-key :haun_kohdejoukko) s/Str
              (s/optional-key :haun_kohdejoukon_tarkenne) s/Str
              })

(def haku-api "%s/tarjonta-service/rest/v1/haku/find?TILA=JULKAISTU&HAKUVUOSI=%s")
(def haku-api-hakukohde-tulos "%s/tarjonta-service/rest/v1/haku/%s/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1")
(def haku-api-koulutus "%s/tarjonta-service/rest/v1/koulutus/search?hakuOid=%s")

(defn haku-to-names [kieli haku]
  (let [nimet (filter #((comp not str/blank?) (last %)) (haku "nimi"))
        koodisto_kieli_nimet (map (fn [e] [(get kieli (first e)) (last e)]) nimet)
        ;a (map #(vector (str "haku_nimi." (get kieli (first %))) (last %)) )
        ]
    ;(into (sorted-map) (filter #((comp not str/blank?) (last %)) a))))
    (into (sorted-map) koodisto_kieli_nimet)))

(defn transform-haku [kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne haku]
  (merge
    {"haku_oid" (haku "oid")
     "haku_nimi" (haku-to-names kieli haku)
     "haun_hakukohteiden_oidit" (haku "hakukohdeOids")
     "hakuvuosi" (haku "hakukausiVuosi")
     "hakukausi" (get kausi (strip-version-from-tarjonta-koodisto-uri (haku "hakukausiUri")))
     "koulutuksen_alkamisvuosi" (haku "koulutuksenAlkamisVuosi")
     "koulutuksen_alkamiskausi" (get kausi (strip-version-from-tarjonta-koodisto-uri (haku "koulutuksenAlkamiskausiUri")))
     "hakutyyppi_koodi" (get hakutyyppi (strip-version-from-tarjonta-koodisto-uri (haku "hakutyyppiUri")))
     "hakutapa_koodi" (get hakutapa (strip-version-from-tarjonta-koodisto-uri (haku "hakutapaUri")))
     "hakukohteiden_priorisointi" (haku "usePriority")
     "haun_kohdejoukko" (get haunkohdejoukko (strip-version-from-tarjonta-koodisto-uri (haku "kohdejoukkoUri")))
     "haun_kohdejoukon_tarkenne" (get haunkohdejoukontarkenne (strip-version-from-tarjonta-koodisto-uri (haku "kohdejoukonTarkenne")))}))

(defn log-fetch [resource-name start-time response]
  (log/debug "Fetching '{}' ready with status {}! Took {}ms!" resource-name (response :status) (- (System/currentTimeMillis) start-time))
  response)

(defn fetch-haku [host-virkailija vuosi]
  (let [start-time (System/currentTimeMillis)
        promise (get-as-promise (format haku-api host-virkailija vuosi))]
    (chain promise (partial log-fetch "haku" start-time) parse-json-body #(% "result"))))

(defn fetch-hakukohde-tulos [host-virkailija haku-oid]
  (let [start-time (System/currentTimeMillis)
        promise (get-as-promise (format haku-api-hakukohde-tulos host-virkailija haku-oid))]
    (chain promise (partial log-fetch "hakukohde-tulos" start-time) parse-json-body #(% "tulokset"))))

(defn- handle-koulutus-result [koulutus-result]
  (mapcat #(get % "tulokset") (get-in koulutus-result ["result" "tulokset"])))

(defn fetch-koulutukset [host-virkailija haku-oid]
  (let [start-time (System/currentTimeMillis)
        promise (get-as-promise (format haku-api-koulutus host-virkailija haku-oid))]
    (chain promise (partial log-fetch "koulutukset" start-time) parse-json-body handle-koulutus-result)))

(defn haku-resource [config vuosi request channel]
  (let [host-virkailija (config :host-virkailija)]
    (-> (let-flow [kieli (fetch-koodisto host-virkailija "kieli")
                   kausi (fetch-koodisto host-virkailija "kausi")
                   hakutyyppi (fetch-koodisto host-virkailija "hakutyyppi")
                   hakutapa (fetch-koodisto host-virkailija "hakutapa")
                   haunkohdejoukko (fetch-koodisto host-virkailija "haunkohdejoukko")
                   haunkohdejoukontarkenne (fetch-koodisto host-virkailija "haunkohdejoukontarkenne")
                   haku (fetch-haku host-virkailija vuosi)]
                  (let [haku-converter (partial transform-haku kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne)
                        converted-hakus (map haku-converter haku)
                        json (to-json converted-hakus)]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel))))
  (schedule-task (* 1000 60 60) (close channel)))


