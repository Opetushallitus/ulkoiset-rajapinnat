(defproject ulkoiset-rajapinnat "0.1.0-SNAPSHOT"
  :description "Opintopolku API"
  :url "http://api.opintopolku.fi"
  :license {:name "EUPL"
            :url "http://www.osor.eu/eupl/"}
  :deploy-repositories {"snapshots" {:url "https://artifactory.oph.ware.fi/artifactory/oph-sade-snapshot-local"}
                        "releases" {:url "https://artifactory.oph.ware.fi/artifactory/oph-sade-release-local"}}
  :repositories [["oph-releases" "https://artifactory.oph.ware.fi/artifactory/oph-sade-release-local"]
                 ["oph-snapshots" "https://artifactory.oph.ware.fi/artifactory/oph-sade-snapshot-local"]
                 ["ext-snapshots" "https://artifactory.oph.ware.fi/artifactory/ext-snapshot-local"]]
  :dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                 [org.clojure/clojure "1.8.0"]
                 [environ "1.1.0"]
                 [compojure "1.6.0"]
                 [manifold "0.1.6"]
                 [http-kit "2.2.0"]
                 [prismatic/schema "1.1.7"]
                 [metosin/compojure-api "1.1.11"]
                 [cheshire "5.8.0"]
                 ; Logging
                 [org.apache.logging.log4j/log4j-api "2.9.0"]
                 [org.apache.logging.log4j/log4j-core "2.9.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.9.0"]
                 [clj-log4j2 "0.2.0"]
                 ]
  :prep-tasks ["compile"]
  :jvm-opts ["-Dlog4j.configurationFile=test/log4j2.properties"
             "-Dulkoisetrajapinnat-properties=test.edn"
             "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"]
  :uberjar-name "ulkoiset-rajapinnat-0.1.0-SNAPSHOT-standalone.jar"
  :main ulkoiset-rajapinnat.core
  :aot [ulkoiset-rajapinnat.core]

  :plugins [[lein-resource "14.10.2"]
            [lein-deploy-artifacts "0.1.0"]]

  :profiles {:uberjar {:prep-tasks ["compile" "resource"]}
             :plugins [[lein-ring "0.11.0"]]
             :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [tempfile "0.2.0"]
                                  [clj-http "3.7.0"]
                                  [ring/ring-mock "0.3.1"]]}})

