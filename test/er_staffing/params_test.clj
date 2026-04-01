(ns er-staffing.params-test
  (:require [clojure.test :refer [deftest is testing]]
            [er-staffing.params :as params]))

(deftest staffing-cost-test
  (let [scenario {:shift-length-hours 8
                  :nurse-hourly-cost 50
                  :pit-hourly-cost 100}
        staffing {:nurses 2 :pit-physicians 1}]
    (is (= 1600.0 (double (params/staffing-cost scenario staffing))))))

(deftest within-budget-test
  (let [scenario {:shift-length-hours 8
                  :nurse-hourly-cost 50
                  :pit-hourly-cost 100
                  :budget-cap-per-shift 1700}
        ok {:nurses 2 :pit-physicians 1}
        bad {:nurses 3 :pit-physicians 1}]
    (is (true? (params/within-budget? scenario ok)))
    (is (false? (params/within-budget? scenario bad)))))
