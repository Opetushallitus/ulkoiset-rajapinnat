(defproject ulkoiset-rajapinnat "0.1.0-SNAPSHOT"
  :description "Opintopolku API"
  :url "http://api.opintopolku.fi"
  :license {:name "EUPL"
            :url "http://www.osor.eu/eupl/"}
  :deploy-repositories {"snapshots" {:url "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"}
                        "releases" {:url "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"}}
  :repositories [["oph-releases" "https://artifactory.opintopolku.fi/artifactory/oph-sade-release-local"]
                 ["oph-snapshots" "https://artifactory.opintopolku.fi/artifactory/oph-sade-snapshot-local"]
                 ["ext-snapshots" "https://artifactory.opintopolku.fi/artifactory/ext-snapshot-local"]
                 ["github" {:url "https://maven.pkg.github.com/Opetushallitus/packages"
                            :username "private-token"
                            :password :env/GITHUB_TOKEN}]]
  :managed-dependencies [[commons-fileupload/commons-fileupload "1.3.3"]
                         [com.fasterxml.jackson.core/jackson-databind "2.9.10.4"]]
  :dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                 ; Java Data
                 [org.flatland/ordered "1.5.7"]
                 [org.clojure/java.data "1.0.95" :exclusions [org.clojure/tools.logging]]
                 ; Snake Camel Kebab
                 [camel-snake-kebab "0.4.3"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.3.443"]
                 [fi.vm.sade/auditlogger "8.0.0-SNAPSHOT"]
                 [environ "1.1.0"]
                 [compojure "1.6.0"]
                 [clj-time "0.14.0"]
                 [clj-http "3.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [fullcontact/full.async "1.0.0"]
                 [http-kit "2.5.3"]
                 [prismatic/schema "1.1.7"]
                 [metosin/compojure-api "1.1.11"]
                 [cheshire "5.8.0"]
                 [org.mongodb/mongodb-driver-reactivestreams "1.6.0"]
                 [clj-soup/clojure-soup "0.1.3"]
                 ; Logging
                 [org.clojure/tools.logging "0.4.0"]
                 [org.apache.logging.log4j/log4j-api "2.17.0"]
                 [org.apache.logging.log4j/log4j-core "2.17.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.17.0"]
                 [clj-log4j2 "0.3.0"]
                 ; Configuration
                 [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]
                 [com.google.code.gson/gson "2.9.0"]
                 [com.github.ben-manes.caffeine/caffeine "3.1.1"]
                 [fi.vm.sade.java-utils/java-cas "1.0.8-SNAPSHOT" :exclusions [org.slf4j/slf4j-simple]]
                 ; Kotlin
                 [org.jetbrains.kotlin/kotlin-stdlib "1.6.21"]
                 ; Coroutines
                 [org.jetbrains.kotlinx/kotlinx-coroutines-jdk8 "1.6.4"]
                 ]
  :prep-tasks ["kotlinc"]
  :jvm-opts ["-Dlog4j.configurationFile=test/log4j2.properties"
             "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"]
  :uberjar-name "ulkoiset-rajapinnat-0.1.0-SNAPSHOT-standalone.jar"
  :main ulkoiset-rajapinnat.core
  :aot [ulkoiset-rajapinnat.core]
  :resource-paths ["resources"]

  :plugins [[lein-resource "14.10.2"]
            [lein-junit "1.1.9"]
            [lein-deploy-artifacts "0.1.0"]
            [me.arrdem/lein-git-version "2.0.8"]
            [kotlinc-lein "0.1.2"]]
  :source-paths ["src/clj" "src/kotlin"]
  :kotlin-source-paths ["src/kotlin"]
  :kotlin-compiler-version "1.6.21"
  :kotlinc-options ["-jvm-target" "1.8" "-language-version" "1.5" "-no-stdlib"]


  :git-version {:version-file      "target/classes/buildversion.edn"
                :version-file-keys [:ref :version :branch :message]}

  :profiles {:uberjar {:prep-tasks ["compile" "kotlinc" "resource"]}
             :plugins [[lein-ring "0.11.0"]]
             :dev {:test-paths ["test/clj" "test/kotlin"]
                   :kotlin-source-paths ["test/kotlin"]
                   :dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [tempfile "0.2.0"]
                                  [junit/junit "4.13"]
                                  [io.github.infeez.kotlin-mock-server/mock-server-core "1.0.0"]
                                  [io.github.infeez.kotlin-mock-server/mock-server-junit4 "1.0.0"]
                                  [com.squareup.okhttp3/mockwebserver "4.9.1"]
                                  [io.github.infeez.kotlin-mock-server/mock-server-okhttp "1.0.0"]
                                  [de.flapdoodle.embed/de.flapdoodle.embed.mongo "2.0.0"]
                                  [org.jetbrains.kotlinx/kotlinx-coroutines-test "1.6.4"]
                                  [ring/ring-mock "0.3.1"]
                                  [audiogum/picomock "0.1.11"]]}})

