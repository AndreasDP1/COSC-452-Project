(ns er-staffing.arrivals
  (:require [er-staffing.random :as rng]))

(defn piecewise-hourly-arrivals
  "Generate patient arrival times (in hours from shift start).
   For each hour h, sample N_h ~ Poisson(rate_h), then place arrivals uniformly in [h, h+1)."
  [^java.util.Random random-generator hourly-arrival-rates shift-length-hours]
  (->> (for [hour (range shift-length-hours)
             :let [lambda (double (nth hourly-arrival-rates hour 0.0))
                   count  (rng/sample-poisson random-generator lambda)]
             i (range count)]
         (+ hour (rng/rand-double random-generator)))
       sort
       vec))

(defn arrival-count-by-hour
  [arrival-times shift-length-hours]
  (reduce
   (fn [acc t]
     (let [h (min (dec shift-length-hours) (int t))]
       (update acc h inc)))
   (vec (repeat shift-length-hours 0))
   arrival-times))
