(ns er-staffing.simulation.metrics-week
  (:require [er-staffing.staffing-v2 :as sv2]))

(defn mean
  [xs]
  (if (seq xs)
    (/ (reduce + xs) (double (count xs)))
    0.0))

(defn summarize-week-replications
  [reps]
  {:avg-wait-time (mean (map :avg-wait-time reps))
   :avg-time-in-system (mean (map :avg-time-in-system reps))
   :avg-queue-length (mean (map :avg-queue-length reps))
   :specialty-matching-rate (mean (map :specialty-matching-rate reps))
   :patients-arrived (mean (map :patients-arrived reps))
   :patients-completed (mean (map :patients-completed reps))})

(defn utilization-by-role-mean
  [reps]
  (into {}
        (for [role sv2/role-order]
          [role (mean (map #(get-in % [:utilization-by-role role] 0.0) reps))])))
