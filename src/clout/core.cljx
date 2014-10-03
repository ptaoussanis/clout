(ns clout.core "Library for parsing the Rails routes syntax."
  #+clj  (:require [clojure.string :as str])
  #+clj  (:import  java.util.Map
                   java.util.regex.Matcher
                   [java.net URLDecoder URLEncoder])
  #+cljs (:require [clojure.string :as str]
                   [goog.string    :as gstr]))

;;;; Regex utils

(def ^:private re-chars
  #+clj  (set "\\.*+|?()[]{}$^")
  #+cljs (reduce #(assoc %1 %2 (str \\ %2)) {} (set "\\.*+|?()[]{}$^")))

(defn- re-escape "Escapes all special regex chars in a string."
  [s]
  #+clj  (str/escape s #(if (re-chars %) (str \\ %)))
  #+cljs (str/escape s re-chars))

#+clj
(defn re-groups*
  "More consistant `re-groups` that always returns a vector of groups, even if
  there is only one group."
  [^Matcher matcher]
  (for [i (range (.groupCount matcher))]
    (.group matcher (int (inc i)))))

;;;; Route matching

(defn path-decode
  "Decodes a path segment in a URI. Defaults to using UTF-8 encoding."
  #+clj ([path] (path-decode path "UTF-8"))
  #+clj ([path encoding]
           (-> (str/replace (str path) "+" (URLEncoder/encode "+" encoding))
               (URLDecoder/decode encoding)))
  #+cljs ([path]
            (-> (str/replace path "+" (js/encodeURI "+"))
                (js/decodeURI))))

;; #+clj
;; (def ^:private uri-chars
;;   "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._~:@!$&'()*+,;=/")

;; #+clj
;; (defmacro ^:private encode-char [char encoding]
;;   `(case ~char
;;      ~@(mapcat (fn [c] [c (str c)]) uri-chars)
;;      \space "%20"
;;      (URLEncoder/encode (Character/toString ~char) ~encoding)))

;; #+clj
;; (defn path-encode
;;   "Encodes a path to make it suitable to be placed in a URI. Default to using
;;   UTF-8 encoding."
;;   ([path] (path-encode path "UTF-8"))
;;   ([path encoding]
;;      (let [sb (StringBuilder.)]
;;        (doseq [c path]
;;          (.append sb ^String (encode-char c encoding)))
;;        (.toString sb))))

(defn- assoc-vec
  "Associates a key with a value. If the key already exists in the map, create a
  vector of values."
  [m k v] (assoc m k (if-let [cur (m k)] (if (vector? cur) (conj cur v) [cur v]) v)))

(defn- assoc-keys-with-groups
  "Creates a hash-map from a series of regex match groups and a collection of
  keywords."
  [groups keys]
  (reduce (fn [m [k v]] (assoc-vec m k v)) {}
    (map vector keys groups)))

(defn- request-url "Returns the complete URL for the request."
  [request]
  (str (name (:scheme request)) "://"
       (get-in request [:headers "host"])
       (:uri request)))

(defn- path-info "Returns the path info for the request."
  [request]
  (or (:path-info request)
      (:uri       request)))

(defprotocol Route
  (route-matches [route request]
    "If the route matches the supplied request, the matched keywords are
    returned as a map. Otherwise, nil is returned."))

(defrecord CompiledRoute [path re keys absolute?]
  Object (toString [_] path)
  Route
  (route-matches [route request]
    (let [path-info (if absolute?
                      (request-url request)
                      (path-info   request))]
      #+clj
      (let [matcher (re-matcher re path-info)]
        (when (.matches matcher)
          ;; (assoc-keys-with-groups
          ;;   (map path-decode (re-groups* matcher))
          ;;   keys)
          (assoc-keys-with-groups (re-groups* matcher) keys)))

      #+cljs
      (when-let [matches (re-matches re path-info)]
        (assoc-keys-with-groups
          (map path-decode (rest matches)) ; Retaining cljs `path-decode`
          keys)))))

;;;; Route compilation

(defn- lex-1
  "Lexes one symbol from a string, and return the symbol and trailing source."
  [src clauses]
  (some
    (fn [[re action]]
      #+clj
      (let [matcher (re-matcher re src)]
        (when (.lookingAt matcher)
          [(if (fn? action) (action matcher) action)
           (subs src (.end matcher))]))
      #+cljs
      (let [matches (.exec re src)]
        (when (gstr/startsWith src (first matches))
          [(if (fn? action) (action matches) action)
           (subs src (count (first matches)))])))
    (partition 2 clauses)))

(defn- lex
  "Lexes a string into tokens by matching against regexs and evaluating
   the matching associated function."
  [src & clauses]
  (loop [results []
         src     src
         clauses clauses]
    (when-let [[result src] (lex-1 src clauses)]
      (let [results (conj results result)]
        (if (= src "")
          results
          (recur results src clauses))))))

(defn- absolute-url? "True if the path contains an absolute or scheme-relative URL."
  [path] (boolean (re-matches #"(https?:)?//.*" path)))

#+clj
(defn- abs-prefix-lex-fn
  "Lex fn for absolute url prefixes. Returns regex string to support
  scheme-relative and/or optional subdomain matching."
  [^Matcher matcher]
  (str
   (if-let [explicit-scheme (.group matcher 1)]
     (str explicit-scheme "//")
     "https?://")
   (when-let [subdoms-qualifier (.group matcher 2)]
     (case subdoms-qualifier
       "?"    "(?:.+\\.)?"  ; Optional subdomain(s)
       "www?" "(?:www\\.)?" ; Optional www subdomain
       nil))))

(defn route-compile
  "Compiles a path string using the routes syntax into a uri-matcher struct."
  ([path] (route-compile path {}))
  ([path regexs]
     #+clj
    (let [splat          #"\*"
          word           #":([\p{L}_][\p{L}_0-9-]*)"
          literal        #"(:[^\p{L}_*]|[^:*/])+"
          abs-prefix     #"^(https?:)?//(www\?|\?)?"
          trailing-slash #"/\?$"
          word-group #(keyword (.group ^Matcher % 1))
          word-regex #(regexs (word-group %) "[^/,;?]+")]
      (CompiledRoute. path
        (re-pattern
          (apply str
            (lex path
              splat   "(.*?)"
              abs-prefix     abs-prefix-lex-fn
              trailing-slash "/?"
              #"/"    "/"
              word    #(str "(" (word-regex %) ")")
              literal #(re-escape (.group ^Matcher %)))))
        (remove nil?
          (lex path
            splat   :*
            abs-prefix     nil
            trailing-slash nil
            #"/"    nil
            word    word-group
            literal nil))
        (absolute-url? path)))

    #+cljs
    (let [splat   #"\*"
          word    #":([^:/.0-9][^:/.]*)"
          literal #"((:[/0-9]+)|[^:*]+)"
          word-group #(keyword (nth % 1))
          word-regex #(regexs (word-group %) #"[^/,;?]+")]
      (CompiledRoute. path
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

(extend-type #+clj String #+cljs string
  Route
  (route-matches [route request]
    (route-matches (route-compile route) request)))
