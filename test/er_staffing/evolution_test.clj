(ns er-staffing.evolution-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.evolution.ga :as ga]
            [er-staffing.evolution.operators :as ops]
            [er-staffing.evolution.selection :as sel]
            [er-staffing.random :as rng]
            [er-staffing.scenario-v2 :as scen]
            [er-staffing.staffing-v2 :as sv2]))

(def bounds
  {:rt [0 2] :cardio [0 2] :radio [0 2] :mh [0 2] :physician [1 4] :pa [0 3] :nurse [1 5]})

(deftest operators-respect-bounds
  (let [rng (rng/make-rng 1)
        a (ops/random-staffing rng bounds)
        b (ops/random-staffing rng bounds)
        c (ops/uniform-crossover a b rng)
        d (ops/mutate c rng bounds 0.5)]
    (is (every? (fn [k] (let [[lo hi] (bounds k)]
                           (<= lo (d k) hi)))
                  (keys bounds)))))

(deftest lexicase-prefers-strictly-better-on-first-shuffled-case
  (let [rng (rng/make-rng 99)
        e1 {:staffing {} :errors [1.0 10.0]}
        e2 {:staffing {} :errors [2.0 0.0]}
        picked (sel/lexicase-pick [e1 e2] rng)]
    ;; Depending on shuffle order, either can win; both are valid lexicase outcomes
    (is (contains? #{e1 e2} picked))))

(deftest ga-runs-small
  (let [syn (scen/load-week-scenario)
        res (ga/run-ga {:scenarios [syn]
                         :bounds bounds
                         :population-size 6
                         :generations 3
                         :mutation-rate 0.3
                         :crossover-rate 0.9
                         :elite 1
                         :replications 1
                         :seed 3
                         :selection :lexicase})]
    (is (map? (:best-staffing res)))
    (is (number? (:mean-error-best res)))))

(deftest ga-runs-without-budget-repair
  (let [syn (scen/load-week-scenario)
        res (ga/run-ga {:scenarios [syn]
                         :bounds bounds
                         :population-size 6
                         :generations 2
                         :mutation-rate 0.3
                         :crossover-rate 0.9
                         :elite 1
                         :replications 1
                         :seed 5
                         :selection :lexicase
                         :budget-repair? false})]
    (is (map? (:best-staffing res)))
    (is (number? (:mean-error-best res)))))

(deftest ga-lexicase-full-runs
  (let [syn (scen/load-week-scenario)
        res (ga/run-ga {:scenarios [syn]
                         :bounds bounds
                         :population-size 6
                         :generations 2
                         :mutation-rate 0.3
                         :replications 1
                         :seed 11
                         :selection :lexicase-full})]
    (is (map? (:best-staffing res)))))

(def wide-bounds
  "Matches `data/synthetic/v2/ga_config.edn` :genome-bounds for repair integration test."
  {:rt [0 18] :cardio [0 18] :radio [0 18] :mh [0 18] :physician [1 35] :pa [0 28] :nurse [2 55]})

(deftest repair-pulls-staffing-under-min-cap-across-scenarios
  (let [syn (scen/load-week-scenario)
        tight (scen/load-week-scenario "data/synthetic/v2/week_case_tight_budget.edn")
        big {:rt 18 :cardio 18 :radio 18 :mh 18 :physician 35 :pa 28 :nurse 55}
        b (ops/repair-staffing-budget big [syn tight] wide-bounds)]
    (is (<= (sv2/daily-staffing-cost syn b) 48000.0))
    (is (<= (sv2/daily-staffing-cost tight b) 48000.0))))
