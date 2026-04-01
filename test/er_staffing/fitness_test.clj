(ns er-staffing.fitness-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.evaluation.fitness :as fitness]))

(deftest budget-penalty-makes-score-worse
  (let [scenario {:shift-length-hours 8
                  :nurse-hourly-cost 50.0
                  :pit-hourly-cost 100.0
                  :budget-cap-per-shift 1000.0}
        ok-staffing {:nurses 2 :pit-physicians 0}
        bad-staffing {:nurses 3 :pit-physicians 0}
        summary {:avg-wait-time 1.0
                 :avg-time-in-system 2.0
                 :avg-total-idle-time 1.0}]
    (is (< (fitness/score-summary scenario ok-staffing summary)
           (fitness/score-summary scenario bad-staffing summary)))))
