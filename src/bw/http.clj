(ns bw.http
  (:require
   [clojure.java.io]
   [clojure.spec.alpha :as s]
   [taoensso.timbre :as log :refer [debug info warn error spy]]
   [me.raynes.fs :as fs]
   [orchestra.core :refer [defn-spec]]
   [clj-http
    [core :as clj-http-core]
    [conn-mgr :as conn]
    [client :as http]
    [headers :as headers]]
   ;;[clojure.core.cache :as cache]
   [taoensso.nippy :as nippy]
   )
  (:import
   [org.apache.commons.io IOUtils]))


(defn cache-key
  "safely encode a URI to something that can live cached on the filesystem"
  [url]
  (let [ext ".nippy" ;;(-> url java.net.URL. .getPath (subs 1) fs/split-ext second (or ""))
        enc (java.util.Base64/getUrlEncoder)]
    (as-> url x
      (str x) (.getBytes x) (.encodeToString enc x) (str x ext))))

(defn slurp-cache-file
  [cache-file]
  (println "Cache hit:" cache-file)
  (let [;; fml: https://stackoverflow.com/questions/26790881/clojure-file-to-byte-array
        f (java.io.File. cache-file)
        ary (byte-array (.length f))
        is (java.io.FileInputStream. f)
        _ (.read is ary)
        data (-> ary nippy/thaw)]
    (.close is)
    data))

(defn spit-cache-file
  [response cache-file]
  (println "Cache miss:" cache-file)
  (when (http/success? response)
    (let [f (java.io.File. cache-file)
          os (java.io.FileOutputStream. f)
          data (-> response
                   (dissoc :http-client)
                   nippy/freeze)]
      (.write os data)
      (.close os)))
  response)

(def expiry-offset-hours 1)

(defn file-older-than
  [path offset-hours]
  (let [now-ms (inst-ms (java.time.Instant/now))
        modtime-ms (fs/mod-time path)
        seconds 1000
        minutes 60
        hours 60]
    (-> now-ms (- modtime-ms) (/ seconds) (/ minutes) (/ hours) int (> offset-hours))))

(defn cache-hit?
  [cache-key]
  (and (fs/exists? cache-key)
       (not (file-older-than cache-key expiry-offset-hours))))

(defn download
  [url & [opts]]
  (let [cm (conn/make-reusable-conn-manager {})

        ;; options:
        ;; https://github.com/dakrone/clj-http/blob/1c751431a3a8d38a795a70609a60cee24ad62757/src/clj_http/core.clj#L208
        ;; http://hc.apache.org/httpcomponents-client-ga/httpclient-cache/apidocs/org/apache/http/impl/client/cache/CacheConfig.Builder.html#setMaxObjectSize(long)
        cache-config (clj-http-core/build-cache-config
                      {:cache-config {:max-object-size 4194304 ;; bytes
                                      :max-cache-entries 100

                                      :heuristic-caching-enabled true
                                      :heuristic-coefficient 0.1 ;; 10% https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
                                      :heuristic-default-lifetime 86400 ;; seconds

                                      }})

        config {:trace-redirects true
                :connection-manager cm
                :cache true
                :cache-config cache-config}

        ;; clj-http options that can be passed through to the request, if they exist
        config (merge config (select-keys opts [:as :http-client :query-params]))

        cache-file (cache-key url)
        
        ]

    (if (cache-hit? cache-file)
      (slurp-cache-file cache-file)
      (spit-cache-file (http/get url config) cache-file))))

(defn download-file
  [url]
  nil)
