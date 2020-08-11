(ns ificator-api.schema
  (:require [schema.core :as s]))

(s/defschema Message
  {:message s/Str})

(s/defschema User
  {:email s/Str})

(s/defschema User-with-password
  (assoc User (s/required-key :password) s/Str))

(s/defschema User-and-token
  (assoc User (s/required-key :authorization) s/Str))

(s/defschema User-with-password-and-token
  (assoc User-with-password (s/required-key :authorization) s/Str))

(s/defschema User-with-role-and-token
  (assoc User-and-token (s/required-key :role) (s/enum "user" "admin")))

(s/defschema Token
  {:token s/Str})

(s/defschema Osm-input
  "EPSG code of crs, example: EPSG:32634.
  Bounding box of area to be converted, coordinates in WGS84."
  {:crs s/Str
   :bbox [s/Num]})

(s/def Osm-input-with-token
  (assoc Osm-input (s/required-key :authorization) s/Str))

(s/defschema Auth-token
  "Token."
  {:authorization s/Str})
