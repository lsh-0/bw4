(defproject bw "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GNU Affero General Public License (AGPL)"
            :url "https://www.gnu.org/licenses/agpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "0.7.559"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [orchestra "2018.12.06-2"] ;; improved clojure.spec instrumentation
                 [org.clojure/tools.namespace "0.3.1"]
                 [com.taoensso/timbre "5.1.0"]
                 [clj-commons/fs "1.5.0"]

                 [juxt/crux-core "20.12-1.13.0-beta"
                  ;; slf4j logging bs: https://github.com/juxt/crux/issues/491
                  :exclusions [org.slf4j/slf4j-api ;; to logback, I did the same for both
                               org.eclipse.rdf4j/rdf4j-rio-ntriples
                               org.eclipse.rdf4j/rdf4j-queryparser-sparql]]
                 [juxt/crux-rocksdb "20.12-1.13.0-beta"]

                 ;; gui-diff depends on an older, buggier version of `ordered`. this prevents that.
                 [org.flatland/ordered "1.5.9"] 
                 [gui-diff "0.6.7"]

                 ;; remember to update the LICENCE.txt
                 ;; remember to update pom file (`lein pom`)

                 [clj-http "3.11.0"]
                 [com.taoensso/nippy "3.1.1"]
                 [cheshire "5.10.0"] ;; json decoding
                 [it.sauronsoftware.cron4j/cron4j "2.2.5"] ;; scheduled tasks

                 [cljfx "1.7.13" :exclusions [org.openjfx/javafx-web
                                              org.openjfx/javafx-media]]
                 [cljfx/css "1.1.0"]

                 [org.openjfx/javafx-base "15.0.1"]
                 [org.openjfx/javafx-base "15.0.1" :classifier "linux"]
                 [org.openjfx/javafx-base "15.0.1" :classifier "mac"]

                 [org.openjfx/javafx-controls "15.0.1"]
                 [org.openjfx/javafx-controls "15.0.1" :classifier "linux"]
                 [org.openjfx/javafx-controls "15.0.1" :classifier "mac"]

                 [org.openjfx/javafx-graphics "15.0.1"]
                 [org.openjfx/javafx-graphics "15.0.1" :classifier "linux"]
                 [org.openjfx/javafx-graphics "15.0.1" :classifier "mac"]
                 
                 ]
  :repl-options {:init-ns bw.ui}

  :plugins [[lein-cljfmt "0.7.0"]
            [jonase/eastwood "0.4.0"]
            [lein-cloverage "1.2.2"]]

  )
