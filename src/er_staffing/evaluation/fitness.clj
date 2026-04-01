(ns er-staffing.evaluation.fitness
  (:require [er-staffing.params :as params]))

(def default-weights
  {:wait-time 0.45
   :time-in-system 0.35
   :idle-time 0.20
   :budget-penalty 10000.0})

(defn score-summary
  ([scenario staffing summary]
   (score-summary scenario staffing summary default-weights))
  ([scenario staffing summary weights]
   (let [cost (params/staffing-cost scenario staffing)
         over-budget? (> cost (:budget-cap-per-shift scenario))
         penalty (if over-budget?
                   (:budget-penalty weights)
                   0.0)]
     (+ (* (:wait-time weights) (:avg-wait-time summary))
        (* (:time-in-system weights) (:avg-time-in-system summary))
        (* (:idle-time weights) (:avg-total-idle-time summary))
        penalty))))
