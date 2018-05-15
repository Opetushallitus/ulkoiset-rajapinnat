(ns ulkoiset-rajapinnat.haku
  (:require [full.async :refer :all]
            [clojure.string :as str]
            [clojure.core.async :refer [go]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel strip-version-from-tarjonta-koodisto-uri]]
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

(defn fetch-haku [vuosi]
  (let [start-time (System/currentTimeMillis)
        mapper (comp #(% "result") parse-json-body (partial log-fetch "haku" start-time))]
    (get-as-channel (resolve-url :tarjonta-service.haku-find-by-hakuvuosi vuosi) {} mapper)))

(defn haku-resource [vuosi request user channel]
  (go
    (try
    (let [kieli (<?? (koodisto-as-channel "kieli"))
          kausi (<?? (koodisto-as-channel "kausi"))
          hakutyyppi (<?? (koodisto-as-channel "hakutyyppi"))
          hakutapa (<?? (koodisto-as-channel "hakutapa"))
          haunkohdejoukko (<?? (koodisto-as-channel "haunkohdejoukko"))
          haunkohdejoukontarkenne (<?? (koodisto-as-channel "haunkohdejoukontarkenne"))
          haku (<?? (fetch-haku vuosi))
          ]
      (let [haku-converter (partial transform-haku kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne)
            converted-hakus (map haku-converter haku)
            json (to-json converted-hakus)
            ]
            (-> channel
                  (status 200)
                  (body-and-close json))))
        (catch Exception e ((exception-response channel) e))))
  (schedule-task (* 1000 60 60) (close channel)))


