(ns er-staffing.simulation.conditions
  (:require [er-staffing.random :as rng]))

(defn active-events-at
  "Events active at simulation time t (hours from week start)."
  [events t]
  (filter (fn [e]
            (and (>= t (double (:start-hour e)))
                 (< t (+ (double (:start-hour e)) (double (:duration e))))))
          events))

(defn merge-condition-probs
  "Apply multiplicative bumps to selected condition categories, then renormalize."
  [base-probs events-at-t]
  (let [bumped
        (reduce (fn [acc evt]
                  (reduce (fn [m [k v]]
                            (update m k (fnil * 1.0) (double v)))
                          acc
                          (or (:condition-multipliers evt) {})))
                base-probs
                events-at-t)
        s (reduce + 0.0 (vals bumped))]
    (if (pos? s)
      (into {} (map (fn [[k v]] [k (/ (double v) s)]) bumped))
      base-probs)))

(defn condition-probs-at
  [scenario t]
  (merge-condition-probs (:condition-probs-baseline scenario)
                         (active-events-at (:events scenario) t)))

(defn base-lambda-at-hour
  "Poisson mean for hour index h when no event multiplier is applied."
  [scenario h]
  (if-let [rates (:hourly-arrival-rates scenario)]
    (double (nth rates h (double (:base-arrival-rate-per-hour scenario))))
    (double (:base-arrival-rate-per-hour scenario))))

(defn arrival-rate-at
  [scenario t]
  (let [h (long t)
        evs (active-events-at (:events scenario) t)
        am (reduce * 1.0 (map #(double (or (:arrival-multiplier %) 1.0)) evs))]
    (* (base-lambda-at-hour scenario h) am)))

(defn hourly-arrival-rates-vector
  "Length = horizon-hours; one Poisson lambda per clock hour."
  [scenario]
  (let [h (:horizon-hours scenario)]
    (mapv #(arrival-rate-at scenario (double %)) (range h))))

(defn sample-condition
  [rng scenario t]
  (let [probs (condition-probs-at scenario t)
        cats (vec (keys probs))
        ps (mapv #(double (get probs %)) cats)
        total (reduce + ps)
        u (* (rng/rand-double rng) total)]
    (loop [i 0 acc 0.0]
      (if (>= i (count cats))
        (peek cats)
        (let [acc' (+ acc (nth ps i))]
          (if (< u acc')
            (nth cats i)
            (recur (inc i) acc')))))))
