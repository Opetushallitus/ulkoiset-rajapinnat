(ns ulkoiset-rajapinnat.valintaperusteet
  (:require [ulkoiset-rajapinnat.utils.rest :refer [parse-json-request status body-and-close body to-json get-as-promise parse-json-body exception-response post-json-with-cas]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [ulkoiset-rajapinnat.utils.snippets :refer [find-first-matching merge-if-not-nil]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clojure.tools.logging :as log]
            [manifold.deferred :refer [let-flow catch chain on-realized zip loop]]))

(def hakukohteet-api "%s/valintaperusteet-service/resources/hakukohde/hakukohteet")
(def valinnanvaiheet-api "%s/valintaperusteet-service/resources/hakukohde/valinnanvaiheet")
(def valintatapajonot-api "%s/valintaperusteet-service/resources/valinnanvaihe/valintatapajonot")
(def hakijaryhmat-api "%s/valintaperusteet-service/resources/hakukohde/hakijaryhmat")
(def valintaryhmat-api "%s/valintaperusteet-service/resources/hakukohde/valintaryhmat")
(def syotettavat-arvot-api "%s/valintaperusteet-service/resources/hakukohde/avaimet")

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
  ([config request channel]
    (valintaperusteet-resource config nil request channel))
  ([config hakukohde-oid request channel]
    (def requested-oids (if (nil? hakukohde-oid) (parse-json-request request) (list hakukohde-oid)))
    (let [host (config :host-virkailija)
          username (config :ulkoiset-rajapinnat-cas-username)
          password (config :ulkoiset-rajapinnat-cas-password)]
      (-> (let-flow [session-id (fetch-jsessionid host "/valintaperusteet-service" username password)
                     post-with-session-id (partial post-json-with-cas host session-id)
                     hakukohteet (post-with-session-id hakukohteet-api requested-oids)
                     hakukohde-oidit (map #(get % "oid") hakukohteet)
                     valinnanvaiheet (post-with-session-id valinnanvaiheet-api hakukohde-oidit)
                     valinnanvaihe-oidit (map #(get % "oid") (flatten (map #(get % "valinnanvaiheet") valinnanvaiheet)))
                     valintatapajonot (post-with-session-id valintatapajonot-api valinnanvaihe-oidit)
                     hakijaryhmat (post-with-session-id hakijaryhmat-api hakukohde-oidit)
                     valintaryhmat (post-with-session-id valintaryhmat-api hakukohde-oidit)
                     syotettavat-arvot (post-with-session-id syotettavat-arvot-api hakukohde-oidit)]
                    (let [json (to-json (result hakukohteet valinnanvaiheet valintatapajonot hakijaryhmat valintaryhmat syotettavat-arvot))]
                      (-> channel
                          (status 200)
                          (body-and-close json))))
          (catch Exception (exception-response channel)))
      (schedule-task (* 1000 60 60) (close channel)))))