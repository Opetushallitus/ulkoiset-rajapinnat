(ns ulkoiset-rajapinnat.haku
  (:require [full.async :refer [<?]]
            [clojure.string :as str]
            [clojure.core.async :refer [go]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.snippets :refer [is-valid-year]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel status body body-and-close exception-response parse-json-body-stream to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [koodisto-as-channel strip-version-from-tarjonta-koodisto-uri]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clj-http.client :as client]))

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

(s/defschema HakemusOid
             {
              (s/optional-key :_id) {}
              (s/optional-key :oid) s/Str
              })

(defn haku-to-names [kieli haku]
  (let [nimet (filter #((comp not str/blank?) (last %)) (haku "nimi"))
        koodisto_kieli_nimet (map (fn [e] [(get kieli (first e)) (last e)]) nimet)]
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
        mapper (comp #(% "result") parse-json-body-stream (partial log-fetch "haku" start-time))]
    (get-as-channel (resolve-url :tarjonta-service.haku-find-by-hakuvuosi vuosi) {:as :stream} mapper)))

(defn haku-resource [vuosi request user channel log-to-access-log]
  (go
    (try
      (if (is-valid-year vuosi)
        (let [kieli (<? (koodisto-as-channel "kieli"))
              kausi (<? (koodisto-as-channel "kausi"))
              hakutyyppi (<? (koodisto-as-channel "hakutyyppi"))
              hakutapa (<? (koodisto-as-channel "hakutapa"))
              haunkohdejoukko (<? (koodisto-as-channel "haunkohdejoukko"))
              haunkohdejoukontarkenne (<? (koodisto-as-channel "haunkohdejoukontarkenne"))
              hakus (<? (fetch-haku vuosi))
              ]
          (let [haku-converter (partial transform-haku kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne)
                converted-hakus (map haku-converter hakus)
                json (to-json converted-hakus)]
            (log-to-access-log 200 nil)
            (-> channel
                (status 200)
                (body-and-close json))))
        (let [message (str "Invalid vuosi parameter " vuosi)
              status-code 400]
          (log-to-access-log status-code message)
          (-> channel
              (status status-code)
              (body-and-close (to-json {:error message})))))
      (catch Exception e
        (log-to-access-log 500 (.getMessage e))
        ((exception-response channel) e))))
  (schedule-task (* 1000 60 60) (close channel)))


; e.g. {"searchTerms":"","asIds":["1.2.246.562.29.26435854158"],"aoOids":[],"states":["ACTIVE", "INCOMPLETE"],"keys":["oid"]}
(defn- hakemus-oids-for-hakuoid-query [haku-oid] {"searchTerms" ""
                                                  "asIds"       [haku-oid]
                                                  "aoOids"      []
                                                  "states"      ["ACTIVE", "INCOMPLETE"]
                                                  "keys"        ["oid"]
                                                  })


(defn fetch-hakemusoids-for-haku-from-haku-app
  [haku-oid request user channel log-to-access-log]
  (let [query (hakemus-oids-for-hakuoid-query haku-oid)
        service-ticket-channel (fetch-service-ticket-channel "/haku-app")]
    (go
      (try
        (log/info "Start reading 'haku-app'...")
        (let [st (<? service-ticket-channel)
              response (let [url (resolve-url :haku-app.listfull)]
                         (log/info (str "POST -> " url))
                         (client/post url {:headers {"CasSecurityTicket" st
                                                     "Content-Type"      "application/json"}
                                           :body    (to-json query)}))
              body (response :body)]
          (-> channel
              (status 200)
              (body-and-close body)))
        (catch Exception e
          (log/error e (format "Problem when reading haku-app for haku %s" haku-oid))
          (-> channel
              (status 500)
              (body-and-close e)))))
    channel))
