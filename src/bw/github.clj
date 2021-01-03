(ns bw.github
  (:require
   [bw.http :as http]))

(defn extract-repo
  "returns a normalised repo from the raw `github-repo` data and any stubs"
  [github-repo]
  (let [key-list [:id :name :full_name :description
                  :created_at :updated_at :pushed_at
                  :archived :disabled :private :fork
                  :open_issues_count :watchers_count :stargazers_count :forks_count
                  :has_downloads :has_issues :has_wiki :has_pages :has_projects
                  :url :mirror_url :size :licence :language
                  :default_branch]
        data (select-keys github-repo key-list)]
    data))

(defn repo-list
  "returns a list of all repositories for given user or org name "
  [user-or-org]
  (let [url (format "https://api.github.com/users/%s/repos" user-or-org)
        params {:as :json
                :query-params {:per_page 100
                               :page 1
                               :sort "full_name"
                               :type "owner"}}
        ]
    (http/download url params)))
