(defproject com.taoensso.forks/clout "2.0.0"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Cross-platform fork of https://github.com/weavejester/clout"
  :url "https://github.com/ptaoussanis/clout"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "Same as Clojure"}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert* true}

  :dependencies
  [[org.clojure/clojure       "1.5.1"]
   [org.clojure/clojurescript "0.0-2322"]]

  ;; :plugins
  ;; [[com.keminglabs/cljx "0.4.0"]
  ;;  [lein-cljsbuild      "1.0.3"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :server-jvm {:jvm-opts ^:replace ["-server"]}
   :1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :1.7  {:dependencies [[org.clojure/clojure  "1.7.0-alpha2"]]}
   :test {:dependencies [[ring-mock "0.1.5"]
                         [criterium "0.4.2"]
                         [com.cemerick/clojurescript.test "0.3.1"]]}
   :dev
   [:1.7 :test
    {:plugins
     [;; These must be in :dev, Ref. https://github.com/lynaghk/cljx/issues/47:
      [com.keminglabs/cljx "0.4.0"]
      [lein-cljsbuild      "1.0.3"]
      ;;
      [lein-pprint         "1.1.1"]
      [lein-ancient        "0.5.5"]]}]}

  :cljx
  {:builds
   [{:source-paths ["src" "test"] :rules :clj  :output-path "target/classes"}
    {:source-paths ["src" "test"] :rules :cljs :output-path "target/classes"}]}

  :cljsbuild
  {:test-commands {"node"    ["node"      "target/main.js"]
                   "phantom" ["phantomjs" "target/main.js"]}
   :builds
   [{:id :main
     :source-paths ["src" "test" "target/classes"]
     :compiler     {:output-to "target/main.js"
                    :optimizations :advanced
                    :pretty-print false}}]}

  ;; :test-paths ["test" "src"]
  ;;:hooks       [cljx.hooks leiningen.cljsbuild]
  ;;:prep-tasks  [["cljx" "once"] "javac" "compile"]
  :prep-tasks    [["with-profile" "+dev" ; Workaround for :dev cljx
                   "cljx" "once"] "javac" "compile"]

  :aliases
  {"test-all"   ["with-profile" "default:+1.6:+1.7" "test"]
   "build-once" ["do" "cljx" "once," "cljsbuild" "once"]
   "deploy-lib" ["do" "build-once," "deploy" "clojars," "install"]
   "start-dev"  ["with-profile" "+server-jvm" "repl" ":headless"]}

  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"})
