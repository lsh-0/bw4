(ns bw.store-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [bw
    [store :as store]
    [core :as core]
    [test-helper :as helper]]))

(deftest put
  (testing "a document (map) can be stored, adding an `id` automatically"
    (helper/with-running-app
      (let [doc {:foo :bar}
            [stored-doc tx] @(store/put doc)]
        (is (contains? stored-doc :crux.db/id))
        (is (= doc (dissoc stored-doc :crux.db/id))))))

  (testing "a document (map) can be stored with a manually set `:id`"
    (helper/with-running-app
      (let [doc {:foo :bar, :crux.db/id :baz} ;; we can also use :id, but just to avoid wrangling...
            [stored-doc tx] @(store/put doc)]
        (is (= doc stored-doc))))))

(deftest get-by-id
  (testing "a document (map) can be retrieved from the store, wrangling the automatic `id`"
    (helper/with-running-app
      (let [doc {:foo :bar} ;; no `id`
            [stored-doc tx] @(store/put doc) ;; automatic `:crux.db/id`
            retrieved-doc (store/get-by-id (:crux.db/id stored-doc))] ;; `:crux.db/id` => `:id`
        (is (contains? retrieved-doc :id))
        (is (= (:crux.db/id stored-doc) (:id retrieved-doc)))
        (is (= doc (dissoc retrieved-doc :id)))))))

(deftest update-doc
  (testing "a document (map) can be updated"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            doc-update (assoc doc :foo :bup)]
        @(store/put doc)
        (is (= doc (store/get-by-id :baz)))
        @(store/put doc-update) ;; same :id
        (is (= doc-update (store/get-by-id :baz)))))))

(deftest doc-history
  (testing "all previous versions of a document (map) can be retrieved"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            doc-update (assoc doc :foo :bup)
            _ @(store/put doc)
            _ @(store/put doc-update) ;; same :id
            [doc-history tx] (store/get-history-by-id :baz)]
        (is (= [doc-update doc] doc-history))))))

(deftest delete-by-id
  (testing "a document (map) can be deleted from the store"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            _ @(store/put doc)]
        (is (= doc (store/get-by-id (:id doc))))
        @(store/delete-by-id (:id doc))
        (is (= nil (store/get-by-id (:id doc))))))))
