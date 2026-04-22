(ns er-staffing.real-profile-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.data.real-profile :as rp]
            [er-staffing.scenario-v2 :as scen]))

(deftest hourly-shares-sum-and-horizon
  (let [shares (rp/read-hourly-shares-24 "data/processed/real/hourly_arrival_profile_from_sample.csv")
        s (reduce + shares)
        lam (rp/shares->weekly-lambdas shares 168 (* 8.0 24.0))]
    (is (= 24 (count shares)))
    (is (< 0.99 s 1.01))
    (is (= 168 (count lam)))))

(deftest attach-real-profile
  (let [sc (scen/load-week-scenario-with-real-hourly-profile)]
    (is (= 168 (count (:hourly-arrival-rates sc))))
    (is (string? (:scenario-id sc)))))
