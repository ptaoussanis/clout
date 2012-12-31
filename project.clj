(defproject com.taoensso.forks/clout "1.2.0"
  :description "A HTTP route matching library"
  :url "https://github.com/weavejester/clout"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]]
  :profiles
  {:dev {:jvm-opts ^:replace []
         :dependencies [[org.clojure/clojure "1.5.1"]
                        [ring-mock "0.1.5"]
                        [criterium "0.4.2"]
                        [com.cemerick/clojurescript.test "0.0.2"]]}
   :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
   :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :plugins [[lein-cljsbuild "0.3.0"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild
  {:builds
   [{:compiler {:output-to "target/clout-debug.js"
                :optimizations :whitespace
                :pretty-print true}
     :source-paths ["src"]}
    {:compiler {:output-to "target/clout-test.js"
                :optimizations :advanced
                :pretty-print true}
     :source-paths ["test"]}
    {:compiler {:output-to "target/clout.js"
                :optimizations :advanced
                :pretty-print false}
     :source-paths ["src"]}]
   :test-commands {"unit-tests" ["runners/phantomjs.js" "target/clout-test.js"]}})
