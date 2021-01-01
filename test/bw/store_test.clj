(ns bw.store-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [bw
    [store :as store]
    [core :as core]
    [test-helper :as helper]]))

(deftest put
  (testing "store module can store a simple document (map), adding an automatic `id`"
    (helper/with-running-app
      (let [doc {:foo :bar}
            [stored-doc tx] @(store/put doc)]
        (is (contains? stored-doc :crux.db/id))
        (is (= doc (dissoc stored-doc :crux.db/id)))))))

(deftest get-by-id
  (testing "store can retrieve documents, wrangling the automatic `id`"
    (helper/with-running-app
      (let [doc {:foo :bar} ;; no `id`
            [stored-doc tx] @(store/put doc) ;; automatic `id`
            retrieved-doc (store/get-by-id (:crux.db/id stored-doc))] ;; `:crux.db/id` => `:id`
        (is (contains? retrieved-doc :id))
        (is (= (:crux.db/id stored-doc) (:id retrieved-doc)))
        (is (= doc (dissoc retrieved-doc :id)))))))
