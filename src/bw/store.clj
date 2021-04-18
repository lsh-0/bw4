(ns bw.store
  (:require
   [taoensso.timbre :refer [log debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.set]
   [bw
    [core :as core :refer [mk-id]]]
   [crux.api :as crux]
   [clojure.java.io :as io]))

(defn node
  []
  (core/get-state :service-state :store :node))

;;

(defn to-crux-doc
  "transforms a document going in to Crux into something more convenient for Crux."
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
  "transforms a document coming out of Crux into something more convenient for the user."
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
  "'puts' a document into the document store.
  See `patch-doc` and `update-doc` for fancier types of 'put'."
  [blob]
  (let [[doc result] (-put blob)]
    (future
      ;; this is *another* transaction on top of the one in `-put`, so the `tx-id` and `tx-time` are different  
      [doc (crux/await-tx (node) result)])))

(defn patch-doc
  "merges `patch-data` over the current entity document"
  [eid patch-data]
  (let [result (crux/submit-tx (node) [[:crux.tx/fn :patch eid patch-data]])]
    (future
      (crux/await-tx (node) result))))

(defn update-doc
  "just like `put`, but if the given `doc` is identical to the previous document version
  the transaction is rolled back."
  [doc]
  (let [crux-doc (to-crux-doc doc)
        result (crux/submit-tx (node) [[:crux.tx/fn :update crux-doc]])]
    (future
      [doc (crux/await-tx (node) result)])))

(defn get-by-id
  "fetches a document using the given `id`"
  [id]
  (from-crux-doc (crux/entity (crux/db (node)) id)))

(defn get-by-id+time
  "fetches a document using the given `id` at a specific point in `time`"
  [id time]
  (from-crux-doc (crux/entity (crux/db (node) time) id)))

(defn exists?
  "returns `true` if given `doc` *exactly* matches one in the database"
  [doc]
  (let [crux-doc (to-crux-doc doc)]
    (some? (spy :info (crux/submit-tx (node) [[:crux.tx/match (:crux.db/id crux-doc) crux-doc]])))))

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

(defn- add-patch-tx
  "creates a 'patch' function that takes an `eid` and `patch-data` and then merges the patch-data over the current document.
  see `patch`."
  []
  (crux/submit-tx
   (node)
   [[:crux.tx/put {:crux.db/id :patch
                   :crux.db/fn '(fn [ctx eid patch-data]
                                  (let [db (crux.api/db ctx)
                                        entity (crux.api/entity db eid)] ;; => {:foo :baz, :crux.db/id :baz}
                                    (when (and (not (nil? entity))
                                               (map? patch-data))
                                      (let [patched-entity (merge entity patch-data)]
                                        (when-not (= entity patched-entity)
                                          [[:crux.tx/put patched-entity]])))))}]]))

(defn- add-update-tx
  "creates a 'update' function that takes a `document` and performs a `put` *but only if the data is different*.
  identical data causes the transaction to be rolled back preventing sequential identical documents."
  []
  (crux/submit-tx
   (node)
   [[:crux.tx/put {:crux.db/id :update
                   :crux.db/fn '(fn [ctx doc]
                                  (let [db (crux.api/db ctx)
                                        eid (:crux.db/id doc)
                                        entity (crux.api/entity db eid)]
                                    (when-not (= doc entity)
                                      [[:crux.tx/put doc]])))}]]))
(defn start-node
  [storage-dir]
  (when storage-dir
    (debug "got storage dir" storage-dir))
  (if storage-dir
    (crux/start-node {:bw4-rocksdb {:crux/module 'crux.rocksdb/->kv-store
                                    :db-dir storage-dir}
                      :crux/document-store {:kv-store :bw4-rocksdb}
                      :crux/tx-log {:kv-store :bw4-rocksdb}})

    ;; in-memory only (for testing)
    ;; "Without any explicit configuration, Crux will start an in-memory node."
    ;; - https://opencrux.com/reference/20.12-1.13.0/configuration.html
    (crux/start-node {})))

(defn init
  "loads any previous database instance"
  []
  (let [node (start-node (core/get-state :service-state :store :storage-dir))]
    (core/set-state :service-state :store :node node)
    (add-patch-tx)
    (add-update-tx)
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
    :get (get-by-id (:id msg))))

(def service-list
  [(core/mkservice :db, :db/store, store-db-service)])
