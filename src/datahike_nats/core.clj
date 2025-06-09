(ns datahike-nats.core
  (:require [datahike.store :refer [empty-store delete-store connect-store scheme->index default-config config-spec]]
            [hitchhiker.tree.bootstrap.konserve :as kons]
            [datahike-nats.nats-store :as nats]
            [environ.core :refer [env]]
            [clojure.spec.alpha :as s]
            [superv.async :refer [<?? S]]))

(defmethod empty-store :nats [config]
  (kons/add-hitchhiker-tree-handlers
   (<?? S (nats/new-nats-store config))))

(defmethod delete-store :nats [config]
  (nats/delete-store config))

(defmethod connect-store :nats [config]
  (<?? S (nats/new-nats-store config)))

(defmethod scheme->index :nats [_]
  :datahike.index/hitchhiker-tree)

(defmethod default-config :nats [config]
  (merge
   {:dbtype "nats"
    :nats-url (:nats-url env "nats://localhost:4222")
    :bucket (:nats-bucket env "datahike")}
   config))

(s/def :datahike.store.nats/backend #{:nats})
(s/def :datahike.store.nats/dbtype string?)
(s/def :datahike.store.nats/nats-url string?)
(s/def :datahike.store.nats/bucket string?)
(s/def ::nats (s/keys :req-un [:datahike.store.nats/backend
                              :datahike.store.nats/dbtype
                              :datahike.store.nats/nats-url
                              :datahike.store.nats/bucket]))

(defmethod config-spec :nats [_] ::nats)

