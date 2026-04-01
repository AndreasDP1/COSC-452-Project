(ns runner
  (:require [clojure.test :as t]
            er-staffing.simulation.stage-a-test
            er-staffing.fitness-test
            er-staffing.params-test
            er-staffing.lexicase-test))

(defn -main
  [& _]
  (let [r (t/run-tests 'er-staffing.simulation.stage-a-test
                       'er-staffing.fitness-test
                       'er-staffing.params-test
                       'er-staffing.lexicase-test)]
    (shutdown-agents)
    (System/exit (if (t/successful? r) 0 1))))
