(ns ificator-api.db
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :as str]
            [clj-time.coerce :as time-coerce]
            [clj-time.core :as clj-time]))

(def db {:dbtype "postgresql"
         :dbname "ificator"
         :host "localhost"
         :port "5432"
         :user "postgres"
         :password "postgres"})

(defn insert-user!
  [email password-hash]
  (sql/insert! db
               :users
               {:email email
                :password_hash password-hash}))

(defn delete-user!
  [email]
  (sql/delete! db
               :users
               ["email = ?" email]))

(defn change-password!
  [email password-hash]
  (sql/update! db
               :users
               {:password_hash password-hash}
               ["email = ?" email]))

(defn insert-role!
  [email role]
  (let [user-id (sql/query db
                           ["SELECT id FROM users WHERE email = ?" (str/lower-case (or email ""))]
                           {:result-set-fn first})
        role-id (sql/query db
                           ["SELECT id FROM roles WHERE name = ?" role]
                           {:result-set-fn first})]
    (println user-id role-id)
    (if (and user-id role-id (not (sql/query db
                                             ["SELECT id, user_id, role_id FROM user_roles WHERE user_id = ? AND role_id = ?" (:id user-id) (:id role-id)]
                                             {:result-set-fn first})))
      (sql/insert! db
                   :user_roles
                   {:user_id (:id user-id)
                    :role_id (:id role-id)}))))

(defn delete-role!
  [email role]
  (let [user-id (sql/query db
                           ["SELECT id FROM users WHERE email = ?" (str/lower-case (or email ""))]
                           {:result-set-fn first})
        role-id (sql/query db
                           ["SELECT id FROM roles WHERE name = ?" role]
                           {:result-set-fn first})]
    (if (and user-id role-id)
      (sql/delete! db
                   :user_roles
                   ["user_id = ? AND role_id = ?" (:id user-id) (:id role-id)]))))

(defn get-user-by-email
  [email]
  (sql/query db
             ["SELECT id, email, password_hash FROM users WHERE lower(email) = ?" (str/lower-case (or email ""))]
             {:result-set-fn first}))

(defn get-user-by-token
  [token]
  (sql/query db
             ["SELECT s.user_id, s.id AS session_id, s.user_agent, s.ip_address, s.anti_forgery_token, s.expires_at, u.email, r.name AS role
             FROM sessions s
             LEFT OUTER JOIN users u ON (s.user_id = u.id)
             LEFT OUTER JOIN user_roles ur ON (s.user_id = ur.user_id)
             LEFT OUTER JOIN roles r ON (ur.role_id = r.id)
             WHERE anti_forgery_token = ? AND s.expires_at > now ()"
              token]
             {:result-set-fn first}))

(defn insert-session
  [user-id session-key user-agent ip-address expires-in anti-forgery-token]
  (sql/insert! db
               :sessions
               {:user_id user-id
                :session_key session-key
                :user_agent user-agent
                :ip_address ip-address
                :expires_at (time-coerce/to-timestamp (clj-time/plus (clj-time/now) (clj-time/minutes expires-in)))
                :anti_forgery_token anti-forgery-token}))

(defn get-session-data
  [key]
  (sql/query db
             ["SELECT s.user_id, s.user_agent, s.ip_address, s.anti_forgery_token, u.email
             FROM sessions s
             LEFT OUTER JOIN users u ON (s.user_id = u.id)
             WHERE session_key = ? and expires_at >"
              key]
             {:result-set-fn first}))

(defn expire-session
  [session-key]
  (sql/update! db
               :session
               {:expires_at (time-coerce/to-timestamp (clj-time/now))}
               ["session_key = ?" session-key]))

(defn invalidate-user-sessions
  [user-id]
  (sql/update! db
               :session
               {:expires_at (time-coerce/to-timestamp (clj-time/now))}
               ["user_id = ?" user-id]))

(defn insert-request
  [session-id user-agent ip-address path-info]
  (sql/insert! db
               :requests
               {:session_id session-id
                :user_agent user-agent
                :ip_address ip-address
                :path_info path-info}))

(defn set-password-hash-for-user
  [password-hash user-id]
  (sql/update! db
               :users
               {:password_hash password-hash}
               ["user_id = ?" user-id]))