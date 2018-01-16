(ns ulkoiset-rajapinnat.haku
  (:require [full.async :refer :all]
            [clojure.string :as str]
            [clojure.core.async :refer [go]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
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

(defn haku-to-names [kieli haku]
  (let [nimet (filter #((comp not str/blank?) (last %)) (haku "nimi"))
        koodisto_kieli_nimet (map (fn [e] [(get kieli (first e)) (last e)]) nimet)
        ]
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
        mapper (comp #(% "result") parse-json-body (partial log-fetch "haku" start-time))]
    (get-as-channel (format haku-api host-virkailija vuosi) {} mapper)))

(defn haku-resource [config vuosi request channel]
  (go
    (try
    (let [host-virkailija (config :host-virkailija)
          kieli (<<?? (koodisto-as-channel config "kieli"))
          kausi (<<?? (koodisto-as-channel config "kausi"))
          hakutyyppi (<<?? (koodisto-as-channel config "hakutyyppi"))
          hakutapa (<<?? (koodisto-as-channel config "hakutapa"))
          haunkohdejoukko (<<?? (koodisto-as-channel config "haunkohdejoukko"))
          haunkohdejoukontarkenne (<<?? (koodisto-as-channel config "haunkohdejoukontarkenne"))
          haku (<<?? (fetch-haku host-virkailija vuosi))
          ]
      (let [haku-converter (apply partial
                                  (into [transform-haku]
                                        (map first [kieli
                                         kausi
                                         hakutyyppi
                                         hakutapa
                                         haunkohdejoukko
                                         haunkohdejoukontarkenne])))
            converted-hakus (map haku-converter (first haku))
            json (to-json converted-hakus)
            ]
            (-> channel
                  (status 200)
                  (body-and-close json))))
        (catch Exception e ((exception-response channel) e))))
  (schedule-task (* 1000 60 60) (close channel)))


