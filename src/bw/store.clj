(ns bw.store
  (:require
   [taoensso.timbre :refer [log debug info warn error spy]]
   [me.raynes.fs :as fs]
   [bw
    [core :as core :refer [mk-id]]]
   [crux.api :as crux]
   [clojure.java.io :as io]))

(defn node
  []
  (core/get-state :service-state :store :node))

;;

(defn to-crux-doc
  [blob]
  (cond
    ;; data blob already has a crux id
    ;; prefer that over any id it may have picked up and pass it through
    (and (map? blob)
         (contains? blob :crux.db/id)) (dissoc blob :id)

    ;; data blob has an id but no crux id, rename :id to crux id
    (and (map? blob)
         (contains? blob :id)) (clojure.set/rename-keys blob {:id :crux.db/id})

    ;; given something that isn't a map.
    ;; wrap in a map, give it an id and pass it through.
    (not (map? blob)) {:crux.db/id (mk-id) :data blob}

    ;; otherwise, it *is* a map but is lacking an id or a crux id.
    :else (assoc blob :crux.db/id (mk-id))))

(defn from-crux-doc
  [result]
  (when result
    (if (map? result)
      (clojure.set/rename-keys result {:crux.db/id :id})

      ;; ... ? just issue a warning and pass it through
      (do
        (warn (str "got unknown type attempting to coerce result from crux db:" (type result)))
        result))))

(defn -put
  [blob]
  (let [doc (to-crux-doc blob)]
    [doc (crux/submit-tx (node) [[:crux.tx/put doc]])]))

(defn put
  [blob]
  (let [[doc result] (-put blob)]
    (future
      ;; this is *another* transaction on top of the one in `-put`, so the `tx-id` and `tx-time` are different  
      [doc (crux/await-tx (node) result)])))

(defn get-by-id
  [id]
  (from-crux-doc (crux/entity (crux/db (node)) id)))

(defn get-by-id+time
  [id time]
  (from-crux-doc (crux/entity (crux/db (node) time) id)))

(defn get-history-by-id
  "returns a pair of lists [document-list, transaction-details]."
  ;; note: not sure if this split is a good idea, we'll see
  [id]
  (let [results (crux/entity-history (crux/db (node)) id :desc {:with-docs? true})
        doc-list (mapv (comp from-crux-doc :crux.db/doc) results)
        tx (mapv #(dissoc % :crux.db/doc) results)]
    [doc-list tx]))

(defn delete-by-id
  "completely deletes document with given `id` including it's version history."
  [id]
  (let [result (crux/submit-tx (node) [[:crux.tx/evict id]])]
    (future
      (crux/await-tx (node) result))))

(defn query-by-type
  [type-kw]
  (crux/q (crux/db (node))
          '{:find [e]
            :where [[e :type type-kw]]}))

;;

(defn start-node
  [storage-dir]
  (info "got storage dir" storage-dir)
  (if storage-dir
    (crux/start-node {:my-rocksdb {:crux/module 'crux.rocksdb/->kv-store
                                   :db-dir (-> storage-dir (io/file "db") fs/absolute str)}
                      :crux/tx-log {:kv-store :my-rocksdb}
                      :crux/document-store {:kv-store :my-rocksdb}
                      })

    ;; in-memory only (for testing)
    ;; "Without any explicit configuration, Crux will start an in-memory node."
    ;; - https://opencrux.com/reference/20.12-1.13.0/configuration.html
    (crux/start-node {})))

(defn init
  "loads any previous database instance"
  []
  (let [node (start-node (core/get-state :service-state :store :storage-dir))]
    (core/set-state :service-state :store :node node)
    (core/add-cleanup #(try
                         (when (core/get-state :service-state :store :storage-dir)
                           ;; if we don't sync before we close then nothing is returned when brought back up.
                           (crux/sync node))
                         (.close node)
                         (catch Exception uncaught-exc
                           ;; hasn't happened yet
                           (error uncaught-exc "uncaught unexception attempting to close crux node"))))))

;;

(defn store-db-service
  [msg]
  (case (:action msg)
    :put (put (:data msg))
    :get (get (:id msg))))

(def service-list
  [(core/mkservice :db, :db/store, store-db-service)])