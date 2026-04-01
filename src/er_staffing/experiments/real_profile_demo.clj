(ns er-staffing.experiments.real-profile-demo
  (:require [er-staffing.data.real-dataset :as real]
            [er-staffing.simulation.stage-a :as sim]
            [er-staffing.evaluation.fitness :as fitness]))

(defn make-scenario-from-real-profile
  []
  {:scenario-id "real-profile-demo"
   :shift-length-hours 24
   :hourly-arrival-rates (real/load-hourly-profile)
   :nurse-service-rate-per-hour 4.5
   :pit-service-rate-per-hour 5.5
   :nurse-hourly-cost 58.0
   :pit-hourly-cost 125.0
   :budget-cap-per-shift 7000.0})

(defn -main
  [& _]
  (let [scenario (make-scenario-from-real-profile)
        staffing {:nurses 4 :pit-physicians 1}
        summary  (sim/run-shift scenario staffing {:replications 5 :seed 42})]
    (println summary)
    (println "score =" (fitness/score-summary scenario staffing summary))))
