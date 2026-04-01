(ns er-staffing.main
  (:require [er-staffing.params :as params]
            [er-staffing.simulation.stage-a :as sim]
            [er-staffing.evaluation.fitness :as fitness]))

(defn demo
  []
  (let [scenario (params/load-scenario)
        staffing {:nurses 3 :pit-physicians 1}
        summary  (sim/run-shift scenario staffing {:replications 10 :seed 42})
        score    (fitness/score-summary scenario staffing summary)]
    (println "Scenario:" (:scenario-id scenario))
    (println "Staffing:" staffing)
    (println "Summary:" summary)
    (println "Score:" score)))

(defn -main
  [& _]
  (demo))
