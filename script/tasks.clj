(ns tasks
  (:require [babashka.process :refer [shell]]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn- reagami-version []
  (:version (json/parse-string (slurp "package.json") true)))

(defn- git-out [& args]
  (str/trim (:out (apply shell {:out :string} args))))

(defn sync-create-app
  "Point create-reagami-app at the current reagami version and commit."
  []
  (let [version (reagami-version)
        ;; ^{commit} because npm makes annotated tags, whose own sha is not the
        ;; commit's
        sha (git-out "git rev-parse --short" (str "v" version "^{commit}"))]
    (spit "create-reagami-app/package.json"
          (str/replace (slurp "create-reagami-app/package.json")
                       #"\"version\": \"[^\"]+\""
                       (str "\"version\": \"" version "\"")))
    ;; the template pins reagami as a git dep, so it needs the tag and sha of
    ;; the release commit
    (spit "create-reagami-app/template/squint.edn"
          (-> (slurp "create-reagami-app/template/squint.edn")
              (str/replace #":git/tag \"[^\"]+\"" (str ":git/tag \"v" version "\""))
              (str/replace #":git/sha \"[^\"]+\"" (str ":git/sha \"" sha "\""))))
    (when-not (str/blank? (git-out "git status --porcelain create-reagami-app"))
      (shell "git add create-reagami-app")
      (shell "git commit -m" (str "create-reagami-app " version)))))

(defn publish
  "Publish reagami and create-reagami-app from the same version."
  []
  (shell "pnpm version patch")
  (sync-create-app)
  (shell "pnpm publish")
  (shell {:dir "create-reagami-app"} "pnpm publish")
  (shell "git push --follow-tags"))
