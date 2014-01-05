(defproject clout "1.1.1-SNAPSHOT"
  :description "A HTTP route matching library"
  :url "http://github.com/weavejester/clout"
  :dependencies [[org.clojure/clojure "1.5.0"]]
  :profiles {:dev {:dependencies [[com.cemerick/clojurescript.test "0.0.2"]
                                  [ring-mock "0.1.4-SNAPSHOT"]]}}
  :plugins [[lein-cljsbuild "0.3.0"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds
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
