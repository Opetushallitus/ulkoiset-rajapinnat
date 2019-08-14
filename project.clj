(defproject ulkoiset-rajapinnat "0.1.0-SNAPSHOT"
  :description "Opintopolku API"
  :url "http://api.opintopolku.fi"
  :license {:name "EUPL"
            :url "http://www.osor.eu/eupl/"}
  :deploy-repositories {"snapshots" {:url "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"}
                        "releases" {:url "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"}}
  :repositories [["oph-releases" "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"]
                 ["oph-snapshots" "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"]
                 ["ext-snapshots" "https://artifactory.opintopolku.fi/artifactory/ext-snapshot-local"]]
  :dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.3.443"]
                 [fi.vm.sade/auditlogger "8.0.0-SNAPSHOT"]
                 [environ "1.1.0"]
                 [compojure "1.6.0"]
                 [clj-time "0.14.0"]
                 [clj-http "3.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [fullcontact/full.async "1.0.0"]
                 [http-kit "2.2.0"]
                 [prismatic/schema "1.1.7"]
                 [metosin/compojure-api "1.1.11"]
                 [cheshire "5.8.0"]
                 [org.mongodb/mongodb-driver-reactivestreams "1.6.0"]
                 [clj-soup/clojure-soup "0.1.3"]
                 ; Logging
                 [org.clojure/tools.logging "0.4.0"]
                 [org.apache.logging.log4j/log4j-api "2.9.0"]
                 [org.apache.logging.log4j/log4j-core "2.9.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.9.0"]
                 [clj-log4j2 "0.2.0"]
                 ; Configuration
                 [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]
                 ]
  :prep-tasks ["compile"]
  :jvm-opts ["-Dlog4j.configurationFile=test/log4j2.properties"
             "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"]
  :uberjar-name "ulkoiset-rajapinnat-0.1.0-SNAPSHOT-standalone.jar"
  :main ulkoiset-rajapinnat.core
  :aot [ulkoiset-rajapinnat.core]
  :resource-paths ["resources"]

  :plugins [[lein-resource "14.10.2"]
            [lein-autoreload "0.1.1"]
            [com.jakemccrary/lein-test-refresh "0.21.1"]
            [lein-deploy-artifacts "0.1.0"]
            [me.arrdem/lein-git-version "2.0.8"]]

  :git-version {:version-file      "target/classes/buildversion.edn"
                :version-file-keys [:ref :version :branch :message]}

  :profiles {:uberjar {:prep-tasks ["compile" "resource"]}
             :plugins [[lein-ring "0.11.0"]]
             :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [tempfile "0.2.0"]
                                  [de.flapdoodle.embed/de.flapdoodle.embed.mongo "2.0.0"]
                                  ;[com.github.fakemongo/fongo "2.1.0"]
                                  [ring/ring-mock "0.3.1"]
                                  [audiogum/picomock "0.1.11"]]}})

