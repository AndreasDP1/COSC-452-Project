(ns er-staffing.evolution.operators
  (:require [er-staffing.random :as rng]
            [er-staffing.staffing-v2 :as sv2]))

(defn repair-staffing-budget
  "Greedy decrement from highest hourly-cost roles until daily staffing cost is <= the
   **minimum** `:daily-budget-cap` across `scenarios` (binding constraint when `:providers`
   match). Respects lower bounds. Prevents runaway over-staffing that only minimizes waits
   while triggering the +10000 budget penalty."
  [staffing scenarios bounds]
  (if (empty? scenarios)
    staffing
    (let [ref-scen (first scenarios)
          cap (double (apply min (map #(double (:daily-budget-cap %)) scenarios)))
          roles-by-cost (sort-by #(double (get-in ref-scen [:providers % :hourly-cost]))
                                 #(compare %2 %1)
                                 sv2/role-order)]
      (loop [s staffing
             guard 0]
        (cond
          (>= guard 10000) s
          (<= (sv2/daily-staffing-cost ref-scen s) cap) s
          :else
          (let [role (some (fn [r]
                             (let [[lo _] (bounds r)
                                   v (long (get s r 0))]
                               (when (> v lo) r)))
                           roles-by-cost)]
            (if role
              (let [[lo _] (bounds role)]
                (recur (update s role (fn [x] (max lo (unchecked-dec (long x)))))
                       (inc guard)))
              s)))))))

(defn random-staffing
  [rng bounds]
  (into {}
        (map (fn [k]
               (let [[lo hi] (bounds k)]
                 [k (rng/rand-int-between rng lo hi)]))
             sv2/role-order)))

(defn uniform-crossover
  [staffing-a staffing-b rng]
  (into {}
        (map (fn [k]
               [k (if (< (rng/rand-double rng) 0.5)
                    (get staffing-a k)
                    (get staffing-b k))])
             sv2/role-order)))

(defn mutate
  [staffing rng bounds per-gene-rate]
  (reduce (fn [m k]
            (if (< (rng/rand-double rng) per-gene-rate)
              (let [[lo hi] (bounds k)]
                (assoc m k (rng/rand-int-between rng lo hi)))
              m))
          staffing
          sv2/role-order))

(comment
  "================================================================================
  FILE: evolution/operators.clj
  NAMESPACE: er-staffing.evolution.operators

  PURPOSE
    GA variation operators: uniform crossover, per-gene mutation, random staffing,
    and **repair-staffing-budget** — greedy reduction of headcount (highest hourly
    wage first) until daily payroll ≤ min cap across given scenarios.

  INPUTS
    repair: staffing map, scenario vector (same :providers), bounds [lo hi] per role.

  OUTPUTS
    Updated staffing maps; all integers within bounds after repair decrements.
  ================================================================================")
