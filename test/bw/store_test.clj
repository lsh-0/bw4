(ns bw.store-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [me.raynes.fs :as fs]
   [bw
    [store :as store]
    [core :as core]
    [test-helper :as helper]]))

(use-fixtures :each helper/fixture-tempcwd)

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
  (testing "a document (map) can be updated with a new version using `put`"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            doc-update (assoc doc :foo :bup)]
        @(store/put doc)
        (is (= doc (store/get-by-id :baz)))
        @(store/put doc-update) ;; same :id
        (is (= doc-update (store/get-by-id :baz)))))))

(deftest update-doc--update
  (testing "a document (map) can be updated with a new version using `update`"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            doc-update (assoc doc :foo :bup)]
        @(store/put doc)
        (is (= doc (store/get-by-id :baz)))
        @(store/update-doc doc-update) ;; same :id
        (is (= doc-update (store/get-by-id :baz)))))))

(deftest update-doc--add
  (testing "a document (map) can also be *added* by using `update`"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}]
        @(store/update-doc doc)
        (is (= doc (store/get-by-id :baz)))))))

(deftest patch-doc
  (testing "a document (map) can be 'patched'"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            patch {:asdf :fdsa}
            expected (merge doc patch)]
        @(store/put doc)
        @(store/patch-doc :baz patch)
        (is (= expected (store/get-by-id :baz)))))))

(deftest patch-doc--missing-doc
  (testing "attempting to patch a document that doesn't exist rolls the transaction back"
    (helper/with-running-app
      (let [;; doc {:foo :bar, :id :baz}
            patch {:asdf :fdsa}]
        ;;@(store/put doc)
        @(store/patch-doc :baz patch)
        ;; nothing created
        (is (nil? (store/get-by-id :baz)))))))

(deftest patch-doc--bad-data
  (testing "attempting to patch a document with non-map data rolls the transaction back"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            patch "hello?"]
        @(store/put doc)
        @(store/patch-doc :baz patch)
        (is (= doc (store/get-by-id :baz)))))))


;; object history

(deftest doc-history
  (testing "all previous versions of a document (map) can be retrieved, ordered most to least recent"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            doc-update (assoc doc :foo :bup)
            _ @(store/put doc)
            _ @(store/put doc-update) ;; same :id
            [doc-history tx] (store/get-history-by-id :baz)]
        (is (= [doc-update doc] doc-history))))))

(deftest doc-history--put-identical-document
  (testing "identical updates to documents do not create new versions"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            doc-update doc
            _ @(store/put doc)
            _ @(store/update-doc doc-update)
            [doc-history tx] (store/get-history-by-id :baz)]
        (is (= [doc-update] doc-history))))))

(deftest doc-history--patch-identical-document
  (testing "a patch that creates an identical document to the previous document in history cannot happen"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            patch {:foo :bar}
            _ @(store/put doc)
            _ @(store/patch-doc :baz patch)
            [doc-history tx] (store/get-history-by-id :baz)]
        (is (= [doc] doc-history))))))

;;

(deftest delete-by-id
  (testing "a document (map) can be deleted from the store"
    (helper/with-running-app
      (let [doc {:foo :bar, :id :baz}
            _ @(store/put doc)]
        (is (= doc (store/get-by-id (:id doc))))
        @(store/delete-by-id (:id doc))
        (is (= nil (store/get-by-id (:id doc))))))))

;; permanent storage

(deftest store-on-disk
  (testing "document store can be persisted on disk"
    (let [doc {:foo :bar, :id :baz}
          opts {:initial-state {:service-state {:store {:storage-dir fs/*cwd*}}}}]

      ;; start app with a disk store and store a document
      (helper/with-running-app+opts opts
        @(store/put doc))

      ;; start app without the disk store and try to fetch the document
      (helper/with-running-app
        (is (= nil (store/get-by-id :baz))))

      ;; start app again with the same disk store and document exists
      (helper/with-running-app+opts opts
        (is (= doc (store/get-by-id :baz)))))))
