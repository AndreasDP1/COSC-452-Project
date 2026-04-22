(ns er-staffing.simulation.random-events
  "Optional stochastic disaster windows: before each run, expand :random-events spec
   into concrete :events (reproducible given RNG seed)."
  (:require [er-staffing.random :as rng]))

(defn realize
  "If :random-events is present, generates :events and removes :random-events.
   Otherwise returns scenario unchanged."
  [scenario rng]
  (if-not (:random-events scenario)
    scenario
    (let [spec (:random-events scenario)
          n (long (or (:n spec) 4))
          h (long (:horizon-hours scenario))
          dmin (long (or (:duration-min spec) 6))
          dmax (long (or (:duration-max spec) 20))
          tpls (vec
                (or (:templates spec)
                    [{:condition-multipliers {:respiratory 2.0} :arrival-multiplier 1.0}
                     {:condition-multipliers {:trauma 2.0 :respiratory 1.2} :arrival-multiplier 1.0}
                     {:condition-multipliers {:psych 2.0} :arrival-multiplier 1.0}
                     {:condition-multipliers {:cardio 1.5} :arrival-multiplier 1.0}]))
          evts (mapv
                (fn [i]
                  (let [dur (rng/rand-int-between rng dmin dmax)
                        start-max (max 0 (- h dur 1))
                        start (if (pos? start-max)
                                (rng/rand-int-between rng 0 start-max)
                                0)
                        tpl (nth tpls (mod i (count tpls)))]
                    (merge {:id (keyword (str "rand-" i))
                            :start-hour (double start)
                            :duration (double dur)}
                           (select-keys tpl [:condition-multipliers :arrival-multiplier]))))
                (range n))]
      (-> scenario
          (assoc :events evts)
          (dissoc :random-events)
          (assoc :scenario-id (str (:scenario-id scenario) "-rand"))))))
