(ns ulkoiset-rajapinnat.valintaperusteet
  (:require [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body-stream post-json-as-channel parse-json-request status body-and-close body to-json parse-json-body exception-response]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching merge-if-not-nil]]
            [org.httpkit.server :refer :all]
            [schema.core :as s]
            [full.async :refer :all]
            [clojure.core.async :as async]
            [org.httpkit.timer :refer :all]
            [clojure.tools.logging :as log]))

(s/defschema Valintaperusteet
  {
   (s/optional-key :tila) s/Str
   (s/optional-key :valintaryhmaOid) s/Str
   (s/optional-key :oid) s/Str
   (s/optional-key :hakuoid) s/Str
   (s/optional-key :nimi) s/Str
   (s/optional-key :tarjoajaOid) s/Str

   (s/optional-key :hakukohdekoodi) {
                                     (s/optional-key :uri) s/Str
                                     (s/optional-key :nimiFi) s/Str
                                     (s/optional-key :nimiSv) s/Str
                                     (s/optional-key :nimiEn) s/Str
                                     (s/optional-key :arvo) s/Str
                                     }
   (s/optional-key :valintaryhma) {
                                   (s/optional-key :vastuuorganisaatioOid) s/Str
                                   (s/optional-key :lapsihakukohde) s/Bool
                                   (s/optional-key :organisaatiot) [s/Str]
                                   (s/optional-key :kohdejoukko) s/Str
                                   (s/optional-key :oid) s/Str
                                   (s/optional-key :hakuoid) s/Str
                                   (s/optional-key :lapsivalintaryhma) s/Bool
                                   (s/optional-key :hakukohdekoodit) [s/Str]
                                   (s/optional-key :nimi) s/Str
                                   (s/optional-key :viimeinenKaynnistyspaiva) s/Str
                                   (s/optional-key :hakuvuosi) s/Str
                                   (s/optional-key :valintakoekoodit) [s/Str]
                                   }
   (s/optional-key :syotettavatArvot) [{
                                       (s/optional-key :vaatiiOsallistumisen) s/Bool
                                       (s/optional-key :funktiotyyppi) s/Str
                                       (s/optional-key :min) s/Str
                                       (s/optional-key :syotettavissaKaikille) s/Bool
                                       (s/optional-key :kuvaus) s/Str
                                       (s/optional-key :max) s/Str
                                       (s/optional-key :tunniste) s/Str
                                       (s/optional-key :tilastoidaan) s/Bool
                                       (s/optional-key :onPakollinen) s/Bool
                                       (s/optional-key :lahde) s/Str
                                       (s/optional-key :syötettavanArvonTyyppi) s/Str
                                       (s/optional-key :arvot) s/Str
                                       (s/optional-key :osallistuminenTunniste) s/Str
                                       }]
   (s/optional-key :valinnanvaiheet) [{
                                      (s/optional-key :nimi) s/Str
                                      (s/optional-key :kuvaus) s/Str
                                      (s/optional-key :aktiivinen) s/Bool
                                      (s/optional-key :valinnanVaiheTyyppi) s/Str
                                      (s/optional-key :oid) s/Str
                                      (s/optional-key :inheritance) s/Bool
                                      (s/optional-key :hasValisijoittelu) s/Bool
                                      (s/optional-key :prioriteetti) s/Int
                                      (s/optional-key :valintatapajonot) [{
                                                                           (s/optional-key :aloituspaikat) s/Int
                                                                           (s/optional-key :tayttojono) s/Str
                                                                           (s/optional-key :tasapistesaanto) s/Str
                                                                           (s/optional-key :eiVarasijatayttoa) s/Bool
                                                                           (s/optional-key :kaikkiEhdonTayttavatHyvaksytaan) s/Bool
                                                                           (s/optional-key :automaattinenLaskentaanSiirto) s/Bool
                                                                           (s/optional-key :kuvaus) s/Str
                                                                           (s/optional-key :siirretaanSijoitteluun) s/Bool
                                                                           (s/optional-key :inheritance) s/Bool
                                                                           (s/optional-key :poissaOlevaTaytto) s/Bool
                                                                           (s/optional-key :prioriteetti) s/Int
                                                                           (s/optional-key :oid) s/Str
                                                                           (s/optional-key :varasijat) s/Int
                                                                           (s/optional-key :aktiivinen) s/Bool
                                                                           (s/optional-key :varasijaTayttoPaivat) s/Int
                                                                           (s/optional-key :poistetaankoHylatyt) s/Bool
                                                                           (s/optional-key :varasijojaKaytetaanAlkaen) s/Str
                                                                           (s/optional-key :nimi) s/Str
                                                                           (s/optional-key :valisijoittelu) s/Bool
                                                                           (s/optional-key :kaytetaanValintalaskentaa) s/Bool
                                                                           (s/optional-key :tyyppi) s/Str
                                                                           (s/optional-key :varasijojaTaytetaanAsti) s/Int
                                                                           }]
                                      }]
   })

(defn result [all-hakukohteet all-valinnanvaiheet all-valintatapajonot all-hakijaryhmat all-valintaryhmat all-syotettavat-arvot]
  (defn collect-hakukohteen-valinnanvaiheet [hakukohde-oid]
    (def hakukohteen-valinnanvaiheet (get (find-first-matching "hakukohdeOid" hakukohde-oid all-valinnanvaiheet) "valinnanvaiheet"))
    (map (fn [valinnanvaihe]
      (def valinnanvaihe-oid (get valinnanvaihe "oid"))
      (def valinnanvaiheen-valintatapajonot (get (find-first-matching "valinnanvaiheOid" valinnanvaihe-oid all-valintatapajonot) "valintatapajonot"))
      (merge-if-not-nil "valintatapajonot" valinnanvaiheen-valintatapajonot valinnanvaihe)
    ) hakukohteen-valinnanvaiheet))

  (map (fn [hakukohde]
    (def hakukohde-oid (get hakukohde "oid"))
    (def hakukohteen-valinnanvaiheet (collect-hakukohteen-valinnanvaiheet hakukohde-oid))
    (def hakukohteen-hakijaryhmat (get (find-first-matching "hakukohdeOid" hakukohde-oid all-hakijaryhmat) "hakijaryhmat"))
    (def hakukohteen-valintaryhmat (get (find-first-matching "hakukohdeOid" hakukohde-oid all-valintaryhmat) "valintaryhma"))
    (def hakukohteen-syotettavat-arvot (get (find-first-matching "hakukohdeOid" hakukohde-oid all-syotettavat-arvot) "valintaperusteDTO"))
    (merge-if-not-nil "syotettavatArvot" hakukohteen-syotettavat-arvot
      (merge-if-not-nil "valintaryhma" hakukohteen-valintaryhmat
        (merge-if-not-nil "hakijaryhmat" hakukohteen-hakijaryhmat
          (merge-if-not-nil "valinnanvaiheet" hakukohteen-valinnanvaiheet hakukohde))))
  ) (filter #(not (nil? %)) all-hakukohteet)))

(defn valintaperusteet-resource
  ([request user channel]
    (valintaperusteet-resource nil request user channel))
  ([hakukohde-oid request user channel]
    (def requested-oids (if (nil? hakukohde-oid) (parse-json-request request) (list hakukohde-oid)))
   (async/go
     (try (let [session-id (<? (fetch-jsessionid-channel "/valintaperusteet-service"))
                post-with-session-id (fn [url data] (post-json-as-channel url data parse-json-body-stream session-id))
                post-if-not-empty (fn [api data] (if (not (empty? data)) (post-with-session-id api data)))
                hakukohteet (<? (post-if-not-empty (resolve-url :valintaperusteet-service.hakukohde-hakukohteet) requested-oids))
                hakukohde-oidit (map #(get % "oid") hakukohteet)
                valinnanvaiheet (<? (post-if-not-empty (resolve-url :valintaperusteet-service.hakukohde-valinnanvaiheet) hakukohde-oidit))
                valinnanvaihe-oidit (map #(get % "oid") (flatten (map #(get % "valinnanvaiheet") valinnanvaiheet)))
                valintatapajonot (<? (post-if-not-empty (resolve-url :valintaperusteet-service.valinnanvaihe-valintatapajonot) valinnanvaihe-oidit))
                hakijaryhmat (<? (post-if-not-empty (resolve-url :valintaperusteet-service.hakukohde-hakijaryhmat) hakukohde-oidit))
                valintaryhmat (<? (post-if-not-empty (resolve-url :valintaperusteet-service.hakukohde-valintaryhmat) hakukohde-oidit))
                syotettavat-arvot (<? (post-if-not-empty (resolve-url :valintaperusteet-service.hakukohde-avaimet) hakukohde-oidit))]
            (let [json (to-json (result hakukohteet valinnanvaiheet valintatapajonot hakijaryhmat valintaryhmat syotettavat-arvot))]
              (log/info (type hakukohteet))
              (-> channel
                  (status 200)
                  (body-and-close json))))
          (catch Exception e
            (do
              (log/error (format "Virhe haettaessa valintaperusteita %d hakukohteelle!" (count requested-oids)), e)
              ((exception-response channel) e)))))
   (schedule-task (* 1000 60 60) (close channel))))