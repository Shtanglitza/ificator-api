(ns ificator-api.server
  (:require [ificator-api.service :as service]
            [ificator-api.config :as config]
            [io.pedestal.http :as bootstrap])
  (:gen-class))

(defonce service-instance nil)

(defn create-server []
  (alter-var-root #'service-instance
                  (constantly (bootstrap/create-server
                                (-> service/service
                                    (assoc ::bootstrap/port (:port config/app-config))
                                    ;(assoc ::bootstrap/container-options (:container-options config/app-config))
                                    (assoc ::bootstrap/host (:host config/app-config))
                                    (bootstrap/default-interceptors))))))

(defn start []
  (when-not service-instance
    (create-server))
  (bootstrap/start service-instance))

(defn stop []
  (when service-instance
    (bootstrap/stop service-instance)))

(defn restart []
  (when service-instance
    (bootstrap/stop service-instance)
    (bootstrap/start service-instance)))

(defn -main [& args]
  (start))