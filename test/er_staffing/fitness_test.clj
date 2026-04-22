(ns er-staffing.fitness-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.scenario-v2 :as scen]
            [er-staffing.evaluation.fitness :as fitness]))

(deftest over-budget-staffing-worsens-score
  (let [scenario (scen/load-week-scenario)
        summary {:avg-wait-time 0.1
                 :avg-time-in-system 0.2
                 :avg-queue-length 0.01
                 :specialty-matching-rate 0.5
                 :within-budget? true}
        ok {:rt 0 :cardio 0 :radio 0 :mh 0 :physician 1 :pa 0 :nurse 1}
        heavy {:rt 3 :cardio 3 :radio 3 :mh 3 :physician 10 :pa 10 :nurse 10}]
    (is (< (fitness/score-summary scenario ok summary)
           (fitness/score-summary scenario heavy summary))
        "Heavy staffing that exceeds daily budget should score worse (higher).")))
