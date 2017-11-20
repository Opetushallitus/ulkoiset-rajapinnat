(ns ulkoiset-rajapinnat.haku
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [fetch-koodisto strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def haku-api "%s/tarjonta-service/rest/v1/haku/find?TILA=JULKAISTU&HAKUVUOSI=%s")

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
    (chain promise parse-json-body result-to-hakus)))

(defn haku-resource [config vuosi request]
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


