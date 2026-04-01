(ns er-staffing.simulation.stage-a-test
  (:require [clojure.test :refer [deftest is testing]]
            [er-staffing.simulation.stage-a :as sim]))

(def scenario
  {:scenario-id "test"
   :shift-length-hours 4
   :hourly-arrival-rates [2 3 3 2]
   :nurse-service-rate-per-hour 4.0
   :pit-service-rate-per-hour 5.0
   :nurse-hourly-cost 50.0
   :pit-hourly-cost 100.0
   :budget-cap-per-shift 1200.0})

(deftest zero-staffing-throws
  (is (thrown? Exception
               (sim/run-one-shift scenario {:nurses 0 :pit-physicians 0}
                                  {:seed 1}))))

(deftest service-records-include-server-idx
  (let [{:keys [service-records]}
        (sim/run-one-shift scenario {:nurses 2 :pit-physicians 1} {:seed 11})]
    (is (pos? (count service-records)))
    (is (every? #(contains? % :server-idx) service-records))))

(deftest run-shift-returns-expected-keys
  (let [summary (sim/run-shift scenario {:nurses 2 :pit-physicians 0}
                               {:replications 3 :seed 123})]
    (is (contains? summary :avg-wait-time))
    (is (contains? summary :avg-time-in-system))
    (is (contains? summary :avg-queue-length))
    (is (contains? summary :avg-total-idle-time))
    (is (contains? summary :nurse-utilization))
    (is (contains? summary :staffing-cost))
    (is (number? (:avg-wait-time summary)))
    (is (<= 0.0 (:nurse-utilization summary) 1.0))
    (is (<= (:avg-wait-time summary) (:avg-time-in-system summary)))))
