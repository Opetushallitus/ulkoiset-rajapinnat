(ns ulkoiset-rajapinnat.odw
  (:require [ulkoiset-rajapinnat.utils.rest :refer [parse-json-request status body-and-close body to-json get-as-promise parse-json-body exception-response post-as-promise]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clojure.tools.logging :as log]
            [manifold.deferred :refer [let-flow catch chain on-realized zip loop]]))

(def hakukohteet-api "%s/valintaperusteet-service/resources/hakukohde/hakukohteet")
(def valinnanvaiheet-api "%s/valintaperusteet-service/resources/hakukohde/valinnanvaiheet")
(def valintatapajonot-api "%s/valintaperusteet-service/resources/valinnanvaihe/valintatapajonot")
(def hakijaryhmat-api "%s/valintaperusteet-service/resources/hakukohde/hakijaryhmat")

(defn handle-response [url response]
  (log/info (str url " " (response :status)))
  ;(log/info (response :body))
  (case (response :status)
    200 (parse-json-body response)
    404 nil
    (throw (RuntimeException. (str "Calling " url " failed: status=" (response :status) ", msg=" (response :body))))))

(defn post-with-url [session-id url body]
  (log/info url)
  ;(log/info body)
  (let [promise (post-as-promise url {:headers {"Cookie" (str "JSESSIONID=" session-id )}} body)]
    (log/info (str url "(JSESSIONID=" session-id ")"))
    (chain promise #(handle-response url %))))

(defn post [host session-id url-template body]
  (post-with-url session-id (format url-template host) body))

(defn find-first-matching [match-key match-value collection]
  (first (filter #(= match-value (get % match-key)) collection)))

(defn merge-if-not-nil [collection merge-key merge-collection]
  (if (nil? merge-collection) collection (merge collection {merge-key merge-collection})))

(defn result [all-hakukohteet all-valinnanvaiheet all-valintatapajonot all-hakijaryhmat]
  (defn collect-hakukohteen-valinnanvaiheet [hakukohde-oid]
    (def hakukohteen-valinnanvaiheet (get (find-first-matching "hakukohdeOid" hakukohde-oid all-valinnanvaiheet) "valinnanvaiheet"))
    (map (fn [valinnanvaihe]
      (def valinnanvaihe-oid (get valinnanvaihe "oid"))
      (def valinnanvaiheen-valintatapajonot (get (find-first-matching "valinnanvaiheOid" valinnanvaihe-oid all-valintatapajonot) "valintatapajonot"))
      (merge-if-not-nil valinnanvaihe "valintatapajonot" valinnanvaiheen-valintatapajonot)
    ) hakukohteen-valinnanvaiheet))

  (map (fn [hakukohde]
    (def hakukohde-oid (get hakukohde "oid"))
    (def hakukohteen-valinnanvaiheet (collect-hakukohteen-valinnanvaiheet hakukohde-oid))
    (def hakukohteen-hakijaryhmat (get (find-first-matching "hakukohdeOid" hakukohde-oid all-hakijaryhmat) "hakijaryhmat"))
    (merge-if-not-nil (merge-if-not-nil hakukohde "valinnanvaiheet" hakukohteen-valinnanvaiheet) "hakijaryhmat" hakukohteen-hakijaryhmat)
  ) (filter #(not (nil? %)) all-hakukohteet)))

(defn odw-resource [config request channel]
  (let [host (config :host-virkailija)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (-> (let-flow [session-id (fetch-jsessionid host "/valintaperusteet-service" username password)
                   post-with-session-id (partial post host session-id)
                   hakukohde-oidit (parse-json-request request)
                   hakukohteet (post-with-session-id hakukohteet-api hakukohde-oidit)
                   valinnanvaiheet (post-with-session-id valinnanvaiheet-api hakukohde-oidit)
                   valinnanvaihe-oidit (map #(get % "oid") valinnanvaiheet)
                   valintatapajonot (post-with-session-id valintatapajonot-api valinnanvaihe-oidit)
                   hakijaryhmat (post-with-session-id hakijaryhmat-api hakukohde-oidit)]
                  (let [json (to-json (result hakukohteet valinnanvaiheet valintatapajonot hakijaryhmat))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel)))
    (schedule-task (* 1000 60 60) (close channel))))