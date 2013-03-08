(ns clout.core
  "Library for parsing the Rails routes syntax."
  (:require [clojure.string :as string]
            [goog.string :as gstring]))

;; Regular expression utilties

(def ^{:private true} re-chars
  (reduce #(assoc %1 %2 (str \\ %2))
          {} (set "\\.*+|?()[]{}$^")))

(defn- re-escape
  "Escape all special regex chars in a string."
  [s] (string/escape s re-chars))

;; Route matching

(defn path-decode
  "Decode a path segment in a URI. Defaults to using UTF-8 encoding."
  [path]
  (-> (string/replace path "+" (js/encodeURI "+"))
      (js/decodeURI)))

(defn- assoc-vec
  "Associate a key with a value. If the key already exists in the map, create a
  vector of values."
  [m k v]
  (assoc m k
         (if-let [cur (m k)]
           (if (vector? cur)
             (conj cur v)
             [cur v])
           v)))

(defn- assoc-keys-with-groups
  "Create a hash-map from a series of regex match groups and a collection of
  keywords."
  [groups keys]
  (reduce
   (fn [m [k v]] (assoc-vec m k v))
   {}
   (map vector keys groups)))

(defn- request-url
  "Return the complete URL for the request."
  [request]
  (str (name (:scheme request))
       "://"
       (get-in request [:headers "host"])
       (:uri request)))

(defn- path-info
  "Return the path info for the request."
  [request]
  (or (:path-info request)
      (:uri request)))

(defprotocol Route
  (route-matches [route request]
    "If the route matches the supplied request, the matched keywords are
    returned as a map. Otherwise, nil is returned."))

(defrecord CompiledRoute [re keys absolute?]
  Route
  (route-matches [route request]
    (let [path-info (if absolute?
                      (request-url request)
                      (path-info request))]
      (if-let [matches (re-matches re path-info)]
        (assoc-keys-with-groups
          (map path-decode (rest matches))
          keys)))))

;; Compile routes

(defn- lex-1
  "Lex one symbol from a string, and return the symbol and trailing source."
  [src clauses]
  (some
   (fn [[re action]]
     (let [matches (.exec re src)]
       (if (gstring/startsWith src (first matches))
         [(if (fn? action) (action matches) action)
          (subs src (count (first matches)))])))
   (partition 2 clauses)))

(defn- lex
  "Lex a string into tokens by matching against regexs and evaluating
   the matching associated function."
  [src & clauses]
  (loop [results []
         src     src
         clauses clauses]
    (if-let [[result src] (lex-1 src clauses)]
      (let [results (conj results result)]
        (if (= src "")
          results
          (recur results src clauses))))))

(defn- absolute-url?
  "True if the path contains an absolute or scheme-relative URL."
  [path]
  (boolean (re-matches #"(https?:)?//.*" path)))

(defn route-compile
  "Compile a path string using the routes syntax into a uri-matcher struct."
  ([path]
     (route-compile path {}))
  ([path regexs]
     (let [splat   #"\*"
           word #":([^:/.0-9][^:/.]*)"
           literal #"((:[/0-9]+)|[^:*]+)"
           word-group #(keyword (nth % 1))
           word-regex #(regexs (word-group %) #"[^/,;?]+")]
       (CompiledRoute.
        (re-pattern
         (apply str
                (lex path
                     splat   "(.*)"
                     #"^//"  "https?://"
                     word    #(str "(" (.-source (word-regex %)) ")")
                     literal #(re-escape (first %1)))))
        (remove nil?
                (lex path
                     splat   :*
                     word    word-group
                     literal nil))
        (absolute-url? path)))))

(extend-type string
  Route
  (route-matches [route request]
    (route-matches (route-compile route) request)))
