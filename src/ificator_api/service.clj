(ns ificator-api.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.interceptor.chain :refer [terminate]]
            [io.pedestal.interceptor :refer [interceptor]]
            [pedestal-api
             [core :as api]
             [helpers :refer [before defbefore defhandler handler]]]
            [ificator-api.schema :as schema]
            [ificator-api.db :as db]
            [schema.core :as s]

            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codecs]
            [buddy.auth :as buddy-auth]
            [buddy.hashers :as hashers]
            [buddy.auth.backends.token :refer [token-backend]]
            [osm-ifc.flat :as osm->ifc-flat]))

(defn ok [d] {:status 200 :body d})
(defn created [d] {:status 201 :body d})
(defn deleted [d] {:status 204 :body d})
(defn bad-request [d] {:status 400 :body d})
(defn unauthorized [d] {:status 401 :body d})
(defn forbidden [d] {:status 403 :body d})
(defn not-found [d] {:status 404 :body d})

(defn random-token
  []
  (let [randomdata (nonce/random-bytes 16)]
    (codecs/bytes->hex randomdata)))

(def token-authentication
  ""
  (interceptor
    {:name ::token-authentication
     :enter (fn [ctx]
              (let [token (:authorization (:query-params (:request ctx)))
                    user (db/get-user-by-token token)]
                (if user
                  (assoc ctx :identity user)
                  ctx)))}))

(def token-authorization
  ""
  (interceptor
    {:name  ::token-authorization
     :enter (fn [ctx]
              (let [role (:role (:identity ctx))]
                (assoc ctx :authorization role)))}))

(def login
  "Example of using the handler helper"
  (handler
    ::login
    {:summary     "Create auth token"
     :parameters  {:query-params schema/User-with-password}
     :responses   {200 {:body schema/Token}
                   401 {:body schema/Message}}}
    (fn [request]
      (let [query-params (:query-params request)
            email (:email query-params)
            password (:password query-params)
            user-db (db/get-user-by-email email)]
        (if (hashers/check password (:password_hash user-db))
          (let [token (random-token)
                user-id (:id user-db)
                user-agent (get (:headers request) "user-agent")
                ip-address (:remote-addr request)]
            (db/insert-session user-id user-agent ip-address 1440 token)
            (ok {:token token}))
          (unauthorized {:message "Unauthorized"}))))))

(def osm->ifc
  "Example of annotating a generic interceptor"
  (api/annotate
    {:summary     "Get ifc file generated from OSM"
     :parameters  {:query-params schema/Osm-input-with-token}
     :responses   {200 {:body s/Str}}}
    (interceptor
      {:name  ::osm-to-ifc
       :enter (fn [ctx]
                (if (buddy-auth/authenticated? ctx)
                  (let [request (:request ctx)
                        query-params (:query-params request)
                        crs (:crs query-params)
                        bbox (:bbox query-params)
                        classes '("rel[building]" "way[building]" "node[building]")
                        data (if (and (< (Math/abs (- (first bbox) (nth bbox 2))) 0.004) (< (Math/abs (- (nth bbox 1) (nth bbox 3))) 0.004))
                               (osm->ifc-flat/create-data "https://www.overpass-api.de/api/" 60 bbox classes crs)
                               false)]
                    (if data
                      (assoc ctx :response (ok data))
                      (assoc ctx :response (bad-request {:message "Maximum difference of lat and lon parameters can be 0.004"}))))
                  (assoc ctx :response (unauthorized {:message "Unauthorized"}))))})))

(def add-user
  "Example of annotating a generic interceptor"
  (api/annotate
    {:summary     "Add a new user"
     :parameters  {:query-params schema/User-with-password-and-token}
     :responses   {201 {:body schema/User}
                   400 {:body schema/Message}
                   401 {:body schema/Message}
                   403 {:body schema/Message}}}
    (interceptor
      {:name  ::add-user
       :enter (fn [ctx]
                (if (buddy-auth/authenticated? ctx)
                  (let [request (:request ctx)
                        query-params (:query-params request)
                        email (:email query-params)
                        password (:password query-params)
                        user-db (db/get-user-by-email email)
                        role (:role (:identity ctx))]
                    (if (= role "admin")
                      (if-not user-db
                        (do
                          (db/insert-user! email (hashers/derive password))
                          (assoc ctx :response (created {:email email})))
                        (assoc ctx :response (bad-request {:message (str "User with email " email " already exists")})))
                      (assoc ctx :response (forbidden {:message "Forbiden"}))))
                  (assoc ctx :response (unauthorized {:message "Unauthorized"}))))})))

(def change-password
  "Example of annotating a generic interceptor"
  (api/annotate
    {:summary     "Change password"
     :parameters  {:query-params schema/User-with-password-and-token}
     :responses   {200 {:body schema/User}
                   400 {:body schema/Message}
                   401 {:body schema/Message}
                   404 {:body schema/Message}}}
    (interceptor
      {:name  ::change-password
       :enter (fn [ctx]
                (if (buddy-auth/authenticated? ctx)
                  (let [request (:request ctx)
                        query-params (:query-params request)
                        email (:email query-params)
                        password (:password query-params)
                        token (:authorization query-params)
                        user-db (db/get-user-by-email email)
                        user-db-2 (db/get-user-by-token token)]
                    (if (and user-db (= (:email user-db) (:email user-db-2)))
                      (do
                        (db/change-password! email (hashers/derive password))
                        (assoc ctx :response (ok {:email email})))
                      (assoc ctx :response (not-found {:message "Not found"}))))
                  (assoc ctx :response (unauthorized {:message "Unauthorized"}))))})))

(def delete-user
  "Example of annotating a generic interceptor"
  (api/annotate
    {:summary     "Delete a user by email"
     :parameters  {:query-params schema/User-and-token}
     :responses   {204 {:body schema/User}
                   400 {:body schema/Message}
                   401 {:body schema/Message}
                   403 {:body schema/Message}
                   404 {:body schema/Message}}}
    (interceptor
      {:name  ::delete-user
       :enter (fn [ctx]
                (if (buddy-auth/authenticated? ctx)
                  (let [request (:request ctx)
                        query-params (:query-params request)
                        email (:email query-params)
                        user-db (db/get-user-by-email email)
                        role (:role (:identity ctx))]
                    (if (= role "admin")
                      (if user-db
                        (do
                          (db/delete-user! email)
                          (assoc ctx :response (ok {:email email})))
                        (assoc ctx :response (not-found {:message (str "Not found")})))
                      (assoc ctx :response (forbidden {:message "Forbiden"}))))
                  (assoc ctx :response (unauthorized {:message "Unauthorized"}))))})))

(def add-role
  "Example of annotating a generic interceptor"
  (api/annotate
    {:summary     "Add a new role to user"
     :parameters  {:query-params schema/User-with-role-and-token}
     :responses   {201 {:body schema/User}
                   400 {:body schema/Message}
                   401 {:body schema/Message}
                   403 {:body schema/Message}
                   404 {:body schema/Message}}}
    (interceptor
      {:name  ::add-role
       :enter (fn [ctx]
                (if (buddy-auth/authenticated? ctx)
                  (let [request (:request ctx)
                        query-params (:query-params request)
                        email (:email query-params)
                        role (:role query-params)
                        user-db (db/get-user-by-email email)
                        auth-role (:role (:identity ctx))]
                    (if (= auth-role "admin")
                      (if user-db
                        (do
                          (db/insert-role! email role)
                          (assoc ctx :response (created {:email email})))
                        (assoc ctx :response (bad-request {:message (str "Not found")})))
                      (assoc ctx :response (forbidden {:message "Forbiden"}))))
                  (assoc ctx :response (unauthorized {:message "Unauthorized"}))))})))

(def delete-role
  "Example of annotating a generic interceptor"
  (api/annotate
    {:summary     "Remove a role from user"
     :parameters  {:query-params schema/User-with-role-and-token}
     :responses   {204 {:body schema/User}
                   400 {:body schema/Message}
                   401 {:body schema/Message}
                   403 {:body schema/Message}
                   404 {:body schema/Message}}}
    (interceptor
      {:name  ::delete-role
       :enter (fn [ctx]
                (if (buddy-auth/authenticated? ctx)
                  (let [request (:request ctx)
                        query-params (:query-params request)
                        email (:email query-params)
                        role (:role query-params)
                        user-db (db/get-user-by-email email)
                        auth-role (:role (:identity ctx))]
                    (if (= auth-role "admin")
                      (if user-db
                        (do
                          (db/delete-role! email role)
                          (assoc ctx :response (ok {:email email})))
                        (assoc ctx :response (not-found {:message (str "Not found")})))
                      (assoc ctx :response (forbidden {:message "Forbiden"}))))
                  (assoc ctx :response (unauthorized {:message "Wrong auth data"}))))})))

(def no-csp
  {:name ::no-csp
   :leave (fn [ctx]
            (assoc-in ctx [:response :headers "Content-Security-Policy"] ""))})

(s/with-fn-validation
  (api/defroutes routes
                 {:info {:title       "Ificator API from Shtanglitza d.o.o."
                         :description "Find out more at https://
                         .rs"
                         :version     "0.1"}
                  :tags [{:name         "Ificator"
                          :description  "Ificator"
                          :externalDocs {:description "Find out more"
                                         :url         "https://ificator.rs"}}
                         {:name        "user"
                          :description "User paths"
                          :externalDocs {:description "Ifc standard"
                                         :url         "https://standards.buildingsmart.org/IFC/DEV/IFC4_2/FINAL/HTML/"}}
                         {:name        "admin"
                          :description "User administration"}]}
                 [[["/" ^:interceptors [api/error-responses
                                        (api/negotiate-response)
                                        (api/body-params)
                                        api/common-body
                                        (api/coerce-request)
                                        (api/validate-response)]
                    ["/user" ^:interceptors [(api/doc {:tags ["user"]})]
                     ["/login" {:post login}]
                     ["/change-password" ^:interceptors [token-authentication] {:put change-password}]]

                    ["/api/ificator" ^:interceptors [(api/doc {:tags ["Ificator"]})]
                     ["/osm-to-ifc" ^:interceptors [token-authentication] {:get osm->ifc}]]

                    ["/admin" ^:interceptors [(api/doc {:tags ["admin"]})]
                     ["/add-user" ^:interceptors [token-authentication] {:post add-user}]
                     ["/remove-user" ^:interceptors [token-authentication] {:delete delete-user}]
                     ["/add-role" ^:interceptors [token-authentication] {:post add-role}]
                     ["/remove-role" ^:interceptors [token-authentication] {:delete delete-role}]]

                    ["/swagger.json" {:get api/swagger-json}]
                    ["/*resource" ^:interceptors [no-csp] {:get api/swagger-ui}]]]]))

(def service
  {:env                      :dev
   ::bootstrap/routes        #(deref #'routes)
   ;; linear-search, and declaring the swagger-ui handler last in the routes,
   ;; is important to avoid the splat param for the UI matching API routes
   ::bootstrap/router        :linear-search
   ::bootstrap/resource-path "/public"
   ::bootstrap/type          :jetty
   ::bootstrap/port          8080
   ::bootstrap/join?         false})