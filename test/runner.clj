(ns runner
  (:require [clojure.test :as t]
            er-staffing.evolution-test
            er-staffing.fitness-test
            er-staffing.lexicase-test
            er-staffing.real-profile-test
            er-staffing.routing-test
            er-staffing.stage-week-test))

(defn -main
  [& _]
  (let [r (t/run-tests 'er-staffing.evolution-test
                       'er-staffing.fitness-test
                       'er-staffing.lexicase-test
                       'er-staffing.real-profile-test
                       'er-staffing.routing-test
                       'er-staffing.stage-week-test)]
    (shutdown-agents)
    (System/exit (if (t/successful? r) 0 1))))
