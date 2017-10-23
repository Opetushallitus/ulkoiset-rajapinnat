(defproject ulkoiset-rajapinnat "0.1.0-SNAPSHOT"
  :description "Opintopolku API"
  :url "http://api.opintopolku.fi"
  :license {:name "EUPL"
            :url "http://www.osor.eu/eupl/"}
  :dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                 [org.clojure/clojure "1.8.0"]
                 [environ "1.1.0"]
                 [compojure "1.6.0"]
                 [manifold "0.1.6"]
                 [http-kit "2.2.0"]
                 [prismatic/schema "1.1.7"]
                 [metosin/compojure-api "1.1.11"]
                 ; Logging
                 [org.apache.logging.log4j/log4j-api "2.9.0"]
                 [org.apache.logging.log4j/log4j-core "2.9.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.9.0"]
                 [clj-log4j2 "0.2.0"]
                 ]

  :jvm-opts ["-Dlog4j.configurationFile=test/log4j2.properties"
             "-Dulkoiset-rajapinnat-properties=test.edn"
             "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"]

  :main ulkoiset-rajapinnat.core

  :profiles {:plugins [[lein-ring "0.11.0"]]
             :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.8.0"]
                                  [ring/ring-mock "0.3.1"]]}})

