(ns er-staffing.scenario-v2
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [er-staffing.data.real-profile :as realp]))

(def default-week-scenario "data/synthetic/v2/week_scenario.edn")

(def default-real-hourly-csv "data/processed/real/hourly_arrival_profile_from_sample.csv")

(defn load-week-scenario
  ([] (load-week-scenario default-week-scenario))
  ([path]
   (with-open [r (java.io.PushbackReader. (io/reader path))]
     (edn/read r))))

(defn load-week-scenario-with-real-hourly-profile
  "Same base scenario as `week_scenario.edn`, but hourly Poisson λ follows the
   empirical within-day shares from the preprocessed hospital CSV (Python script)."
  ([] (load-week-scenario-with-real-hourly-profile default-week-scenario default-real-hourly-csv))
  ([scenario-path csv-path]
   (-> (load-week-scenario scenario-path)
       (realp/attach-real-hourly-profile csv-path))))

(comment
  "================================================================================
  FILE: scenario_v2.clj
  NAMESPACE: er-staffing.scenario-v2

  PURPOSE
    Load week-long scenario EDN from disk; optional **real hourly profile** merge
    (hospital CSV -> :hourly-arrival-rates) while keeping same expected daily volume.

  INPUTS
    Paths to EDN and optionally CSV (default under data/processed/real/).

  OUTPUTS
    Scenario map consumed by simulation.stage-week and fitness.
  ================================================================================")
