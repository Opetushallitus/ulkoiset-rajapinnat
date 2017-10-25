(ns ulkoiset-rajapinnat.tarjonta
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response response-to-json to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def haku-api "%s/tarjonta-service/rest/v1/haku/find?TILA=JULKAISTU&HAKUVUOSI=%s")
(def koodisto-api "%s/koodisto-service/rest/codeelement/codes/%s/1")

(defn strip-version-from-tarjonta-koodisto-uri [tarjonta-koodisto-uri]
  (if-let [uri tarjonta-koodisto-uri]
    (first (str/split uri #"#"))
    nil))

(defn haku-to-names [kieli haku]
  (let [a (map #(vector (str "haku_nimi." (get kieli (first %))) (last %)) (haku "nimi"))]
    (into (sorted-map) (filter #((comp not str/blank?) (last %)) a))))

(defn transform-haku [kieli kausi hakutyyppi hakutapa haunkohdejoukko haunkohdejoukontarkenne haku]
  (merge
    (haku-to-names kieli haku)
    {"haku_oid" (haku "oid")
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

(defn result-to-hakus [result]
  (result "result"))

(defn fetch-haku [host-virkailija vuosi]
  (let [promise (get-as-promise (format haku-api host-virkailija vuosi))]
    (chain promise response-to-json result-to-hakus)))

(defn transform-uri-to-arvo-format [koodisto]
  [(koodisto "koodiUri") (koodisto "koodiArvo")])

(defn fetch-koodisto [host-virkailija koodisto]
  (let [promise (get-as-promise (format koodisto-api host-virkailija koodisto))]
    (chain promise response-to-json #(map transform-uri-to-arvo-format %) #(into (sorted-map) %))))

(defn tarjonta-resource [config vuosi request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
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
                (schedule-task (* 1000 60 60) (close channel))))


