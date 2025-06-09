(ns datahike-nats.nats-store
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.core.async :as async :refer [go]]
            [konserve.protocols :refer [PEDNAsyncKeyValueStore PKeyIterable]])
  (:import [io.nats.client Nats Connection JetStreamManagement]
           [io.nats.client.api KeyValueConfiguration]
           [io.nats.client.jetstream KeyValue]))

(defn- kv-key [k]
  (cond
    (keyword? k) (name k)
    (sequential? k) (str/join "/" (map kv-key k))
    :else (str k)))

(defrecord NatsKVStore [^Connection conn ^KeyValue kv]
  PEDNAsyncKeyValueStore
  (-exists? [_ k]
    (go (boolean (.get kv (kv-key k)))))
  (-get-meta [_ _]
    (go nil))
  (-get-version [_ _]
    (go nil))
  (-get [_ k]
    (go (when-let [entry (.get kv (kv-key k))]
          (-> entry .getValue String. edn/read-string)))
  (-update-in [this [f & r] meta-up up-fn args]
    (go
      (let [entry (.get kv (kv-key f))
            cur   (when entry (edn/read-string (String. (.getValue entry))))
            old    (if r (get-in cur r) cur)
            new-val (if r
                       (apply update-in (or cur {}) r up-fn args)
                       (apply up-fn cur args))]
        (.put kv (kv-key f) (.getBytes (pr-str new-val)))
        [old new-val])))
  (-assoc-in [this key-vec meta val]
    (go
      (let [[f & r] key-vec
            entry (.get kv (kv-key f))
            cur   (when entry (edn/read-string (String. (.getValue entry))))
            new-val (if r
                       (assoc-in (or cur {}) r val)
                       val)]
        (.put kv (kv-key f) (.getBytes (pr-str new-val)))
        nil)))
  (-dissoc [_ k]
    (go (.delete kv (kv-key k))))
  PKeyIterable
  (-keys [_]
    (go (set (.keys kv)))))

(defn connect
  [{:keys [nats-url bucket]}]
  (let [^Connection c (Nats/connect nats-url)
        ^JetStreamManagement jsm (.jetStreamManagement c)
        kv (try
              (.getKeyValue jsm bucket)
              (catch Exception _
                (.createKeyValue jsm (-> (KeyValueConfiguration/builder)
                                          (.name bucket)
                                          (.build)))
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
