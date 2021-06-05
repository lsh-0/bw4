(ns bw.specs
  (:require
   [clojure.spec.alpha :as s]
   [me.raynes.fs :as fs]))


(defn some-id?
  "returns `true` if given `id` value is any good"
  [id]
  (or (and (string? id)
           (not (clojure.string/blank? id)))
      (qualified-keyword? id)))

;; --

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

(s/def ::service map?)

(s/def ::id (s/or :uuid uuid? :some-id some-id?))
(s/def ::type #{:github/repo :scheduler/schedule})

(s/def ::boardwalk-type (s/keys :req-un [::id ::type]))

;;

(s/def :scheduler/cron string?)
(s/def :scheduler/schedule (s/merge ::boardwalk-type
                                    (s/keys :req-un [:scheduler/cron])))

;;

(s/def :github/repo (s/merge ::boardwalk-type
                             (s/keys :req-un [])))
(s/def :github/repo-list (s/coll-of :github/repo))
