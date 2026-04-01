(ns er-staffing.experiments.baseline-grid
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [er-staffing.params :as params]
            [er-staffing.simulation.stage-a :as sim]
            [er-staffing.evaluation.fitness :as fitness]))

(defn staffing-grid
  [{:keys [min-nurses max-nurses min-pit max-pit]}]
  (for [n (range min-nurses (inc max-nurses))
        p (range min-pit (inc max-pit))]
    {:nurses n :pit-physicians p}))

(defn evaluate-grid
  [scenario bounds sim-config]
  (for [staffing (staffing-grid bounds)]
    (let [summary (sim/run-shift scenario staffing sim-config)]
      (assoc summary :score (fitness/score-summary scenario staffing summary)))))

(defn write-grid-csv!
  [path rows]
  (with-open [w (io/writer path)]
    (csv/write-csv w
                   (cons ["scenario-id" "nurses" "pit-physicians" "score" "avg-wait-time" "avg-time-in-system"
                          "avg-queue-length" "avg-total-idle-time" "nurse-utilization" "pit-utilization"
                          "patients-arrived" "patients-completed" "staffing-cost" "within-budget?"]
                         (for [r rows]
                           [(:scenario-id r)
                            (get-in r [:staffing :nurses])
                            (get-in r [:staffing :pit-physicians])
                            (:score r)
                            (:avg-wait-time r)
                            (:avg-time-in-system r)
                            (:avg-queue-length r)
                            (:avg-total-idle-time r)
                            (:nurse-utilization r)
                            (:pit-utilization r)
                            (:patients-arrived r)
                            (:patients-completed r)
                            (:staffing-cost r)
                            (:within-budget? r)])))))

(defn -main
  [& _]
  (let [scenario (params/load-scenario)
        rows     (evaluate-grid scenario
                                {:min-nurses 1 :max-nurses 6
                                 :min-pit 0 :max-pit 3}
                                {:replications 10 :seed 42})
        out      "results/placeholders/base_grid_results.csv"]
    (write-grid-csv! out rows)
    (println "Wrote" out)))
