(defproject ulkoiset-rajapinnat "0.1.0-SNAPSHOT"
  :description "Opintopolku API"
  :url "http://api.opintopolku.fi"
  :license {:name "EUPL"
            :url "http://www.osor.eu/eupl/"}
  :dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                 [org.clojure/clojure "1.8.0"]
                 [compojure "1.6.0"]
                 [manifold "0.1.6"]
                 [http-kit "2.2.0"]
                 [prismatic/schema "1.1.7"]
                 [metosin/compojure-api "1.1.11"]
                 ]

  :main ulkoiset-rajapinnat.core

  :profiles {:plugins [[lein-ring "0.11.0"]]
             :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.8.0"]
                                  [ring/ring-mock "0.3.1"]]}})

