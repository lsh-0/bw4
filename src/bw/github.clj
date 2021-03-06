(ns bw.github
  (:require
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [clojure.set :refer [rename-keys]]
   [bw
    [specs :as sp]
    [core :as core]
    [utils :as utils]
    [http :as http]]))

(defn-spec repo-list map? ;;:github/repo-list
  "returns a list of all repositories for given user or org name "
  [user-or-org string?]
  (let [url (format "https://api.github.com/users/%s/repos" user-or-org)
        params {:as :json
                :query-params {:per_page 100
                               :page 1
                               :sort "full_name"
                               :type "owner"}}]
    ;; todo: handle unsuccessful requests somehow
    (http/download url params)))

(defn-spec extract-repo :github/repo
  "returns a normalised repo from the raw `github-repo` data and any stubs"
  [github-repo map?]
  (let [key-list [:id :name :full_name :description :html_url
                  :created_at :updated_at :pushed_at
                  :archived :disabled :private :fork
                  :open_issues_count :watchers_count :stargazers_count :forks_count
                  :has_downloads :has_issues :has_wiki :has_pages :has_projects
                  :url :mirror_url :size :licence :language
                  :default_branch]

        rename-map {:created_at :dt-created-at, :updated_at :dt-updated-at, :pushed_at :dt-pushed-at,
                    :has_downloads :has-downloads?, :has_issues :has-issues?, :has_wiki :has-wiki?
                    :has_pages :has-pages?, :has_projects, :has-projects?
                    :archived :archived?, :disabled :disabled?, :private :private?, :fork :fork?}

        data (-> github-repo
                 (select-keys key-list)
                 (rename-keys rename-map)
                 utils/underscores-to-hyphens)

        ;; used for boardwalk storage and retrieval
        updates {:id (keyword "github" (str (:id data))) ;; => `:github/1234567890`
                 :type :github/repo}]

    (merge data updates)))

;; ---

(defn get-repo-list
  "fetches and returns a list of repositories"
  [{user-or-org :message}]
  (->> user-or-org bw.github/repo-list :body (map bw.github/extract-repo)))

(def service-list
  [(core/mkservice :github, :github/get-repo-list, get-repo-list)])
