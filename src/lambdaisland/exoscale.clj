(ns lambdaisland.exoscale
  "Low-level wrapper for the Exoscale HTTP API

  Provides HTTP handling, JSON decoding, and request signing."
  (:require [babashka.process :refer [process]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [java-http-clj.core :as http]
            [lambdaisland.uri :as uri]
            [cheshire.core :as json]))

(def v1-base-url (uri/uri "https://api.exoscale.com"))
(def v2-base-url (uri/uri "https://api-ch-gva-2.exoscale.com"))

(defn creds
  "Try to find the Exoscale API credentials

  Looks in order at
  - ~/.config/exoscale/exoscale.toml (same as `exo` CLI)
  - $EXOSCALE_API_KEY / $EXOSCALE_API_SECRET
  - $TF_VAR_exoscale_api_key / $TF_VAR_exoscale_secret_key"
  []
  (let [creds (try
                (slurp (str (System/getenv "HOME") "/.config/exoscale/exoscale.toml"))
                (catch Exception e))]
    [(or (second (re-find #"key\s=\s\"(.*)\"" creds))
         (System/getenv "EXOSCALE_API_KEY")
         (System/getenv "TF_VAR_exoscale_api_key"))
     (or (second (re-find #"secret\s=\s\"(.*)\"" creds))
         (System/getenv "EXOSCALE_API_SECRET")
         (System/getenv "TF_VAR_exoscale_secret_key"))]))

(defn- stream->bytes
  "Convert an input-stream to a bytes array"
  [is]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy is baos)
    (.toByteArray baos)))

(defn- hmac
  "Compute a base64 digest of a HMAC-SHA256

  Relies on the openssl CLI for portability (babashka)"
  [key input type]
  (String.
   (.encode (java.util.Base64/getEncoder)
            (stream->bytes
             (:out @(process
                     ["openssl" "dgst" (case type
                                         :sha256 "-sha256"
                                         :sha1 "-sha1")
                      "-hmac" key "-binary"]
                     {:in input}))))))

(defn auth-header
  "Generate the Authorization header including signature"
  [{:keys [method uri body api-key api-secret]}]
  (let [query-params (into (sorted-map)
                           (uri/query-map uri))
        ts (long (/ (System/currentTimeMillis) 1000))
        msg (str/join
             "\n"
             [(str (str/upper-case (name method)) " " (:path uri))
              body
              (apply str (mapcat #(if (vector? %) % [%]) (vals query-params)))
              ""
              ts])]
    (str "EXO2-HMAC-SHA256 credential=" api-key
         (when (seq query-params)
           (str
            ",signed-query-args="
            (str/join ";" (map name (mapcat #(if (vector? %) % [%]) (keys query-params))))))
         ",expires=" ts
         ",signature=" (hmac api-secret msg :sha256))))

(defn api-request-v1
  ([method path]
   (api-request-v1 method path nil))
  ([method path opts]
   (let [[key secret] (:creds opts (creds))
         uri (uri/assoc-query (uri/join v1-base-url path) :apikey key)
         signature (hmac
                    secret
                    (str/lower-case
                     (uri/map->query-string
                      (into (sorted-map)
                            (uri/query-map uri))))
                    :sha1)
         uri (uri/assoc-query uri :signature signature)]
     (assert (and key secret))
     (update (http/send
              (merge {:uri (uri/uri-str uri)
                      :method method}
                     opts))
             :body
             (fn [body]
               (try
                 (with-meta
                   (json/parse-string body)
                   {:raw body})
                 (catch Exception e
                   {:parse-error e
                    :body body})))))))

(defn api-request-v2
  "Perform an API request to the v2 API"
  ([method path]
   (api-request-v2 method path nil))
  ([method path opts]
   (let [[key secret] (:creds opts (creds))
         uri (uri/join v2-base-url path)]
     (assert (and key secret))
     (update (http/send
              (merge {:uri (uri/uri-str uri)
                      :method method
                      :headers {"Authorization"
                                (auth-header {:method method
                                              :uri uri
                                              :body (:body opts)
                                              :api-key key
                                              :api-secret secret})}}
                     opts))
             :body
             (fn [body]
               (try
                 (with-meta
                   (json/parse-string body)
                   {:raw body})
                 (catch Exception e
                   {:parse-error e
                    :body body})))))))

(def get-v1 (partial api-request-v1 :get))
(def post-v1 (partial api-request-v1 :post))
(def put-v1 (partial api-request-v1 :put))
(def delete-v1 (partial api-request-v1 :delete))

(def get-v2 (partial api-request-v2 :get))
(def post-v2 (partial api-request-v2 :post))
(def put-v2 (partial api-request-v2 :put))
(def delete-v2 (partial api-request-v2 :delete))

(comment
  (get-v1 "/compute?command=listVirtualMachines")
  (get-v2 "/v2/zone")
  (get-v2 "/v2/instance"))
