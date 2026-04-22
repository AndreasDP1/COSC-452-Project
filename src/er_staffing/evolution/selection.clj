(ns er-staffing.evolution.selection
  (:require [er-staffing.evolution.evaluation :as eva]
            [er-staffing.random :as rng]))

(defn lexicase-pick
  "evaluated: vector of {:staffing :errors}; returns one element."
  [evaluated rng]
  (if (= 1 (count evaluated))
    (first evaluated)
    (let [n-c (count (:errors (first evaluated)))
          order (rng/shuffle-with-rng rng (range n-c))]
      (loop [pool evaluated [ci & more] order]
        (if (= 1 (count pool))
          (first pool)
          (if (nil? ci)
            (first (rng/shuffle-with-rng rng pool))
            (let [best (apply min (map #(nth (:errors %) ci) pool))
                  nxt (vec (filter #(= (nth (:errors %) ci) best) pool))]
              (recur (if (seq nxt) nxt pool) more))))))))

(defn tournament-pick
  [evaluated rng k]
  (let [sample (take k (rng/shuffle-with-rng rng evaluated))]
    (first (sort-by #(eva/mean-error (:errors %)) sample))))

(defn lexicase-select-n-without-replacement
  "Full environmental lexicase: pick `n` distinct individuals by running lexicase on the
   remaining pool each time (no replacement). Used when `:selection` is `:lexicase-full`."
  [evaluated rng n]
  (loop [pool (vec evaluated) out []]
    (if (or (>= (count out) n) (empty? pool))
      (vec (take n out))
      (let [w (lexicase-pick pool rng)
            idx (some (fn [i]
                        (when (= (:staffing (nth pool i)) (:staffing w)) i))
                      (range (count pool)))
            _ (when (nil? idx) (throw (ex-info "lexicase pick not in pool" {})))
            pool' (vec (concat (subvec pool 0 idx) (subvec pool (inc idx))))]
        (recur pool' (conj out w))))))

(defn pick-parent
  [evaluated rng {:keys [selection tournament-size]}]
  (case selection
    :lexicase (lexicase-pick evaluated rng)
    :tournament (tournament-pick evaluated rng (or tournament-size 3))
    (lexicase-pick evaluated rng)))

(comment
  "================================================================================
  FILE: evolution/selection.clj
  NAMESPACE: er-staffing.evolution.selection

  PURPOSE
    **Parent selection** for GA: lexicase (shuffle case order, filter pool by best
    error on each case), tournament sampling, or environmental lexicase without
    replacement for :lexicase-full mode.

  INPUTS
    evaluated — vector of {:staffing :errors}; errors length = number of scenarios.

  NOTE
    This is the implementation **used by the running GA**. It is separate from
    `evaluation/lexicase.clj` (offline analysis helpers).
  ================================================================================")
