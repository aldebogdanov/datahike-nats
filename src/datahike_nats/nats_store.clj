(ns datahike-nats.nats-store
  (:require [clojure.edn :as edn]
            [konserve.core :as k])
  (:import [io.nats.client Nats Connection JetStreamManagement JetStream]
           [io.nats.client.api KeyValueConfiguration]
           [io.nats.client.jetstream KeyValue]))

(defrecord NatsKVStore [^Connection conn ^KeyValue kv]
  k/KVStore
  (read [_ k]
    (future
      (when-let [entry (.get kv (name k))]
        (-> entry .getValue slurp edn/read-string))))
  (write [_ k v]
    (future
      (.put kv (name k) (.getBytes (pr-str v)))
      v))
  (delete [_ k]
    (future (.delete kv (name k))))
  (exists? [_ k]
    (future (boolean (.get kv (name k)))))
  (keys [_]
    (future (iterator-seq (.keys kv))))
  (cas! [_ _ _]
    (future (throw (UnsupportedOperationException. "cas! not implemented")))))

(defn connect
  [{:keys [nats-url bucket]}]
  (let [^Connection c (Nats/connect nats-url)
        ^JetStreamManagement jsm (.jetStreamManagement c)
        kv (try
              (.getKeyValue jsm bucket)
              (catch Exception _
                (.createKeyValue jsm (.build (doto (KeyValueConfiguration/builder)
                                              (.name bucket))))
                (.getKeyValue jsm bucket)))]
    (->NatsKVStore c kv)))

(defn new-nats-store [config]
  (connect config))

(defn delete-store
  [{:keys [nats-url bucket]}]
  (let [^Connection c (Nats/connect nats-url)
        ^JetStreamManagement jsm (.jetStreamManagement c)]
    (try (.deleteKeyValue jsm bucket) (catch Exception _))
    (.close c)))
