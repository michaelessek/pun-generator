(ns server
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :refer [resource]]
   [clojure.math.combinatorics :refer [cartesian-product]]
   [clojure.set :refer [intersection]]
   [clojure.string :as string :refer [split split-lines]]
   [core :refer [get-ipa normalized-edn ipa-edn]]
   [libpython-clj2.python :refer [from-import]]
   [libpython-clj2.require]
   [mount.core :refer [defstate start]]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.util.response :refer [content-type not-found resource-response response]]))

(from-import Levenshtein distance)

(def phrase-scores
  (edn/read-string (slurp (resource normalized-edn))))

(def phrase-ipas
  (edn/read-string (slurp (resource ipa-edn))))

(def has-space?
  (partial re-find #" "))

(def recognizability-threshold
  50)

(def recognizable-phrases
  (map first (filter (comp (partial < recognizability-threshold) second) phrase-scores)))

(def recognizable-words
  (remove has-space? recognizable-phrases))

(defn calculate-normalized-distance
  [original replacement]
  (/ (distance original replacement) (count original)))

(def similarity-threshold
  0.5)

(defn find-similar-words
  [word]
  (->> recognizable-words
       (select-keys phrase-ipas)
       (filter (comp (partial > similarity-threshold)
                     (partial calculate-normalized-distance (get-ipa word))
                     last))
       (map first)))

(def recognizable-multi-word-phrases
  (filter has-space? recognizable-phrases))

(defn create-boundary-regex
  [word]
  (re-pattern (str "\\b" word "\\b")))

(defn generate-puns
  [substitute-word]
  (let [similar-words (find-similar-words substitute-word)]
    (->> recognizable-multi-word-phrases
; Efficiently generates puns by filtering phrases prior to the cartesian product.
; This drastically reduces intermediate computations and allocations.
; Observed ~5558ms -> ~916ms elapsed time for `(time (doall (generate-puns "pun")))`
         (remove (comp empty?
                       (partial intersection (set similar-words))
                       set
                       #(split % #" ")))
         (cartesian-product similar-words)
         (mapcat (fn [[original-word phrase]]
                   (if (and (re-find (create-boundary-regex original-word) phrase)
                            (not= original-word substitute-word))
                     [(string/replace phrase (create-boundary-regex original-word) substitute-word)]
                     [])))
         distinct)))

(def default-ssaamm-limit
  10)

(def max-ssaamm-limit
  100)

(defn normalize-limit
  [raw-limit]
  (let [limit (try
                (Integer/parseInt (str raw-limit))
                (catch Exception _ default-ssaamm-limit))]
    (-> limit
        (max 1)
        (min max-ssaamm-limit))))

(defn replace-word-at-index
  [phrase index replacement]
  (let [words (vec (split phrase #" "))
        original (get words index)]
    (when (and original
               (not= (string/lower-case original) (string/lower-case replacement)))
      (string/join " " (assoc words index replacement)))))

(defn generate-puns-ssaamm
  [substitute-word limit]
  (let [target-ipa (get-ipa substitute-word)]
    (if (string/blank? target-ipa)
      []
      (->> recognizable-multi-word-phrases
           (mapcat (fn [phrase]
                     (let [words (split phrase #" ")]
                       (keep-indexed
                        (fn [index word]
                          (let [word-ipa (get phrase-ipas word)
                                pun (replace-word-at-index phrase index substitute-word)]
                            (when (and word-ipa
                                       (pos? (count word-ipa))
                                       pun)
                              [(/ (distance target-ipa word-ipa) (count word-ipa)) pun])))
                        words))))
           (sort-by first)
           (map second)
           distinct
           (take limit)))))

(defn normalize-targets
  [targets]
  (->> targets
       (map string/trim)
       (remove string/blank?)))

(defn extract-targets
  [body]
  (cond
    (sequential? body)
    (normalize-targets body)

    (map? body)
    (let [input (or (get body :input) (get body "input"))
          targets (or (get body :targets) (get body "targets"))]
      (cond
        (string? input) (normalize-targets (split-lines input))
        (sequential? targets) (normalize-targets targets)
        :else []))

    :else []))

(defn normalize-mode
  [raw-mode]
  (let [mode (-> (or raw-mode "ours")
                 str
                 string/lower-case)]
    (if (#{"ours" "ssaamm"} mode)
      mode
      "ours")))

(defn extract-options
  [body]
  (if (map? body)
    {:targets (extract-targets body)
     :mode (normalize-mode (or (get body :mode) (get body "mode")))
     :limit (normalize-limit (or (get body :limit) (get body "limit")))}
    {:targets (extract-targets body)
     :mode "ours"
     :limit default-ssaamm-limit}))

(defn generate-response
  [{:keys [targets mode limit]}]
  (let [puns (if (= mode "ssaamm")
               (->> targets
                    (mapcat #(generate-puns-ssaamm % limit))
                    distinct
                    (take limit))
               (sort (set (mapcat generate-puns targets))))]
    (response puns)))

(defn api-handler
  [request]
  (generate-response (extract-options (:body request))))

(def api-app
  (wrap-json-response (wrap-json-body api-handler)))

(defn html-home
  []
  (if-let [res (resource-response "index.html")]
    (content-type res "text/html; charset=utf-8")
    (not-found "Missing index.html")))

(defn app
  [request]
  (let [method (:request-method request)
        uri (:uri request)]
    (cond
      (and (= method :get) (= uri "/")) (html-home)
      (and (= method :post)
           (or (= uri "/") (= uri "/api/puns"))) (api-app request)
      :else (not-found "Not found"))))

(defstate server
  :start (run-jetty app {:join? false
                         :port 3000})
  :stop (.stop server))

(defn -main
  []
  (start)
  @(promise))
