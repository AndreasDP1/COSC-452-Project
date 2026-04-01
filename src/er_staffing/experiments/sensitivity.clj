(ns er-staffing.experiments.sensitivity
  (:require [er-staffing.params :as params]
            [er-staffing.simulation.stage-a :as sim]
            [er-staffing.evaluation.fitness :as fitness]))

(defn multiply-arrivals
  [scenario factor]
  (update scenario :hourly-arrival-rates
          (fn [rates] (mapv #(* factor %) rates))))

(defn run-sensitivity
  [scenario staffing]
  (for [factor [0.8 1.0 1.2 1.4]]
    (let [scenario' (multiply-arrivals scenario factor)
          summary   (sim/run-shift scenario' staffing {:replications 10 :seed 42})]
      {:factor factor
       :score (fitness/score-summary scenario' staffing summary)
       :summary summary})))

(defn -main
  [& _]
  (let [scenario (params/load-scenario)
        staffing {:nurses 3 :pit-physicians 1}]
    (doseq [row (run-sensitivity scenario staffing)]
      (println row))))
