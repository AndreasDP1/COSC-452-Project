(ns er-staffing.stage-week-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.scenario-v2 :as scen]
            [er-staffing.simulation.stage-week :as week]))

(deftest run-week-returns-keys
  (let [scenario (scen/load-week-scenario)
        staffing {:rt 1 :cardio 0 :radio 0 :mh 0 :physician 1 :pa 0 :nurse 1}
        s (week/run-week scenario staffing {:replications 1 :seed 1})]
    (is (contains? s :avg-wait-time))
    (is (contains? s :specialty-matching-rate))
    (is (contains? s :within-budget?))
    (is (map? (:utilization-by-role s)))))
