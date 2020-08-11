(ns ificator-api.config)

(def app-config {:port 5000

                 :session-life 1440

                 :db {:dbtype "postgresql"
                      :dbname "ificator"
                      :host "localhost"
                      :port "5432"
                      :user "postgres"
                      :password "postgres"}
                 :host "0.0.0.0"})
