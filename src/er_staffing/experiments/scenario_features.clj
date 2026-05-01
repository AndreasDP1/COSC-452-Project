(ns er-staffing.experiments.scenario-features
  "Convert one scenario EDN into JSON features expected by predict_genome.py.

   Usage:
     clojure -M -m er-staffing.experiments.scenario-features <scenario.edn> <out.json>"
  (:require [clojure.data.json :as json]
            [er-staffing.scenario-v2 :as scen]))

(defn- normalize
  [xs]
  (let [s (reduce + 0.0 xs)]
    (if (pos? s)
      (mapv #(/ (double %) s) xs)
      (vec (repeat (count xs) (/ 1.0 (double (count xs))))))))

(defn- hourly-shares-24
  [scenario]
  (if-let [rates (:hourly-arrival-rates scenario)]
    (->> rates
         (take 24)
         (mapv double)
         normalize)
    (vec (repeat 24 (/ 1.0 24.0)))))

(defn- event-feature-pairs
  [events]
  (mapcat
   (fn [i]
     (let [evt (nth events i nil)
           cm (or (:condition-multipliers evt) {})]
       [[(format "evt%d_present" (inc i)) (if evt 1 0)]
        [(format "evt%d_start_hour" (inc i)) (double (or (:start-hour evt) 0.0))]
        [(format "evt%d_duration" (inc i)) (double (or (:duration evt) 0.0))]
        [(format "evt%d_arrival_multiplier" (inc i)) (double (or (:arrival-multiplier evt) 1.0))]
        [(format "evt%d_mult_respiratory" (inc i)) (double (get cm :respiratory 1.0))]
        [(format "evt%d_mult_cardio" (inc i)) (double (get cm :cardio 1.0))]
        [(format "evt%d_mult_trauma" (inc i)) (double (get cm :trauma 1.0))]
        [(format "evt%d_mult_psych" (inc i)) (double (get cm :psych 1.0))]
        [(format "evt%d_mult_generic" (inc i)) (double (get cm :generic 1.0))]]))
   (range 6)))

(defn scenario->features
  [scenario]
  (let [shares (hourly-shares-24 scenario)
        profile-type (if (:hourly-arrival-rates scenario) "real_hourly" "flat")
        cprob (:condition-probs-baseline scenario)
        events (vec (:events scenario))
        base
        [["base_arrival_rate_per_hour" (double (:base-arrival-rate-per-hour scenario))]
         ["queue_threshold" (long (or (get-in scenario [:routing :queue-threshold]) 12))]
         ["wait_threshold_hours" (double (or (get-in scenario [:routing :wait-threshold-hours]) 1.5))]
         ["p_respiratory" (double (get cprob :respiratory 0.0))]
         ["p_cardio" (double (get cprob :cardio 0.0))]
         ["p_trauma" (double (get cprob :trauma 0.0))]
         ["p_psych" (double (get cprob :psych 0.0))]
         ["p_generic" (double (get cprob :generic 0.0))]
         ["arrival_profile_type" profile-type]
         ["daily_budget_cap" (double (:daily-budget-cap scenario))]]
        arr (map-indexed (fn [h v] [(format "arr_share_h%02d" h) (double v)]) shares)
        ev (event-feature-pairs events)]
    (into {} (concat base arr ev))))

(defn -main
  [& args]
  (let [[scenario-path out-path] args]
    (when-not (and scenario-path out-path)
      (throw (ex-info "Usage: <scenario.edn> <out.json>" {:args args})))
    (let [scenario (scen/load-week-scenario scenario-path)
          features (scenario->features scenario)]
      (spit out-path (json/write-str features))
      (println "Wrote feature JSON:" out-path))))
