(ns bw.specs
  (:require
   [clojure.spec.alpha :as s]
   [me.raynes.fs :as fs]))

(s/def ::file (s/or :obj #(instance? java.io.File %)
                    :str (s/and string? 
                                    #(try (and % (java.io.File. %))
                                          (catch java.lang.IllegalArgumentException e
                                            false)))))
(s/def ::extant-file (s/and ::file fs/exists?))

;; directory must be a string and a valid File object, but not necessarily exist (yet).
;; can't test if it's a directory until it exists.
(s/def ::dir ::file) 
(s/def ::extant-dir (s/and ::dir fs/directory?))
