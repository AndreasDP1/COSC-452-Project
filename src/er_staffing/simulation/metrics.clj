(ns er-staffing.simulation.metrics)

(defn mean
  [xs]
  (if (seq xs)
    (/ (reduce + xs) (double (count xs)))
    0.0))

(defn summarize-replications
  [replications]
  {:avg-wait-time        (mean (map :avg-wait-time replications))
   :avg-time-in-system   (mean (map :avg-time-in-system replications))
   :avg-queue-length     (mean (map :avg-queue-length replications))
   :avg-total-idle-time  (mean (map :avg-total-idle-time replications))
   :nurse-utilization    (mean (map :nurse-utilization replications))
   :pit-utilization      (mean (map :pit-utilization replications))
   :patients-arrived     (mean (map :patients-arrived replications))
   :patients-completed   (mean (map :patients-completed replications))})
