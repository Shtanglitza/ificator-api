(defproject ificator-api "0.0.1"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [prismatic/schema "1.1.12"]
                 [io.pedestal/pedestal.service "0.5.8"]
                 [io.pedestal/pedestal.service-tools "0.5.8"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.8"]
                 ;; [io.pedestal/pedestal.immutant "0.5.8"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.8"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.30"]
                 [org.slf4j/jcl-over-slf4j "1.7.30"]
                 [org.slf4j/log4j-over-slf4j "1.7.30"]
                 [org.clojure/tools.logging "1.1.0"]

                 [buddy/buddy-auth "2.2.0"]
                 [buddy/buddy-hashers "1.4.0"]

                 [org.clojure/java.jdbc "0.7.11"]
                 [org.postgresql/postgresql "42.1.4"]
                 [clj-time "0.15.2"]
                 [org.clojure/data.json "1.0.0"]

                 [osm-ifc/osm-ifc "0.1.0"]
                 [ifc-tools-clj/ifc-tools-clj "0.1.0-SNAPSHOT"]
                 [pedestal-api "24ccbed83f1a7309b307a9249ff40e1e1a100e83"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:uberjar {:aot [ificator-api.server]}}
  :main ^{:skip-aot true} ificator-api.server
  :plugins [[s3-wagon-private "1.3.4"]
            [reifyhealth/lein-git-down "0.3.7"]
            [camechis/deploy-uberjar "0.3.0"]]
  :repositories [["public-github" {:url "git://github.com/"}]
                 ["shtanglitza" {:url "s3p://shtanglitza/clojure"
                                 :username :env/AWS_ACCESS_KEY_ID
                                 :passphrase :env/AWS_SECRET_ACCESS_KEY}]]
  :middleware [lein-git-down.plugin/inject-properties]
  :git-down {pedestal-api {:coordinates shtanglitza/pedestal-api}}
  :uberjar-name "ificator-api.jar"
  :target-path "target")