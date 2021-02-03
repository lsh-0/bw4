(ns bw.gui
  (:require
   [me.raynes.fs :as fs]
   [taoensso.timbre :as timbre :refer [spy info]] ;; debug info warn error spy]] 
   [cljfx.ext.table-view :as fx.ext.table-view]
   [cljfx.lifecycle :as fx.lifecycle]
   [cljfx.component :as fx.component]
   [cljfx.ext.list-view :as fx.ext.list-view]
   [cljfx
    [api :as fx]]
   ;;[cljfx.css :as css]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [bw
    [ui :as ui]
    [specs :as sp]
    [utils :as utils]
    [core :as core]])
  (:import
   [java.util List]
   [javafx.util Callback]
   [javafx.scene.control TableRow TextInputDialog Alert Alert$AlertType ButtonType]
   [javafx.stage FileChooser FileChooser$ExtensionFilter DirectoryChooser Window WindowEvent]
   [javafx.application Platform]
   [javafx.scene Node]))

(defn get-window
  "returns the application `Window` object."
  []
  (first (Window/getWindows)))
  
(defn exit-handler
  "exit the application. if running while testing or within a repl, it just closes the window"
  [& [_]]
  (cond
    ;; fresh repl => (restart) => (:in-repl? @state) => nil
    ;; because the app hasn't been started and there is no state yet, the app will exit.
    ;; when hitting ctrl-c while the gui is running, `(utils/in-repl?) => false` because it's running on the JavaFX thread,
    ;; and so will exit again there :( the double-check here seems to work though.
    (or (:in-repl? @core/state)
        (utils/in-repl?)) (swap! core/state assoc-in [:ui :gui-showing?] false)
    (-> timbre/*config* :testing?) (swap! core/state assoc-in [:ui :gui-showing?] false)
    ;; 2020-08-08: `ss/invoke-later` was keeping the old window around when running outside of repl.
    ;; `ss/invoke-soon` seems to fix that.
    ;;  - http://daveray.github.io/seesaw/seesaw.invoke-api.html
    ;; 2020-09-27: similar issue in javafx
    :else (Platform/runLater (fn []
                               (Platform/exit)
                               (System/exit 0)))))


(defn object-box
  [{:keys [fx/context]}]
  (let [selected-list (fx/sub-val context get-in [:app-state, :ui :selected-list])]
    {:fx/type :text
     :text (with-out-str
             (clojure.pprint/pprint selected-list))}))

(defn list-view [{:keys [items selection selection-mode on-change renderer]}]
  {:fx/type fx.ext.list-view/with-selection-props
   :props (case selection-mode
            :multiple {:selection-mode :multiple
                       :selected-items selection
                       :on-selected-items-changed on-change}
            :single (cond-> {:selection-mode :single
                             :on-selected-item-changed on-change}
                      (seq selection)
                      (assoc :selected-item (-> selection sort first))))
   :desc {:fx/type :list-view
          :cell-factory {:fx/cell-type :list-cell
                         :describe (fn [path]
                                     {:text (renderer path)})}
          :items items}})

(defn result-list-list
  [{:keys [fx/context]}]
  (let [result-list (fx/sub-val context get-in [:app-state, :ui :result-list])
        selected-list (fx/sub-val context get-in [:app-state, :ui :selected-list])
        ]
    {:fx/type list-view
     :selection-mode :multiple
     :items result-list
     :selection selected-list
     :on-change ui/select-result
     :renderer str ;; todo: need a 'single-textual-line' renderer here. 
     }))

(defn app
  "returns a description of the javafx Stage, Scene and the 'root' node.
  the root node is the top-most node from which all others are descendents of."
  [{:keys [fx/context]}]
  (let [showing? (fx/sub-val context get-in [:app-state, :ui :gui-showing?])
        ]
    {:fx/type :stage
     :showing showing?
     :on-close-request exit-handler
     :title "boardwalk"
     :width 1024
     :height 768
     :scene {:fx/type :scene
             :root {:fx/type :v-box
                    :children [{:fx/type :split-pane
                                :divider-positions [0.5]
                                :orientation :horizontal
                                :items [{:fx/type :v-box
                                         :children [{:fx/type result-list-list}]}

                                        {:fx/type :v-box
                                         :children [{:fx/type object-box}]}]}]
                    }}}))

;;

(defn start
  []
  (info "starting gui")
  (let [;; the gui uses a copy of the application state because the state atom needs to be wrapped
        state-template {:app-state nil,
                        }
        gui-state (atom (fx/create-context state-template)) ;; cache/lru-cache-factory))
        update-gui-state (fn [new-state]
                           (swap! gui-state fx/swap-context assoc :app-state new-state))
        _ (core/state-bind [] update-gui-state)

        renderer (fx/create-renderer
                  :middleware (comp
                               fx/wrap-context-desc
                               (fx/wrap-map-desc (fn [_] {:fx/type app})))

                  ;; magic :(

                  :opts {:fx.opt/type->lifecycle #(or (fx/keyword->lifecycle %)
                                                      ;; For functions in `:fx/type` values, pass
                                                      ;; context from option map to these functions
                                                      (fx/fn->lifecycle-with-context %))})

        ;; don't do this, renderer has to be unmounted and the app closed before further state changes happen during cleanup
        ;;_ (core/add-cleanup #(fx/unmount-renderer gui-state renderer))
        _ (swap! core/state assoc :disable-gui (fn []
                                                 (fx/unmount-renderer gui-state renderer)
                                                 ;; the slightest of delays allows any final rendering to happen before the exit-handler is called.
                                                 ;; only affects testing from the repl apparently and not `./run-tests.sh`
                                                 (Thread/sleep 25)))

        ]

    (swap! core/state assoc-in [:ui :gui-showing?] true)
    (fx/mount-renderer gui-state renderer)

    ;; `refresh` the app but kill the `refresh` if app is closed before it finishes.
    ;; happens during testing and causes a few weird windows to hang around.
    ;; see `(mapv (fn [_] (test :gui)) (range 0 100))`
    ;;(let [kick (future
    ;;             (core/refresh))]
    ;;  (core/add-cleanup #(future-cancel kick)))

    ;; calling the `renderer` will re-render the GUI.
    ;; useful apparently, but not being used.
    ;;renderer
    ))

(defn stop
  []
  (info "stopping gui")
  (when core/state
    (when-let [unmount-renderer (:disable-gui @core/state)]
      ;; only affects tests running from repl apparently
      (unmount-renderer))
    (exit-handler))
  nil)
