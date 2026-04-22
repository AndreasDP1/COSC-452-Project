(ns er-staffing.data.real-profile
  "Load hourly arrival *shares* from the Python-preprocessed CSV and build a
   per-clock-hour Poisson λ vector for the week simulator."
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- read-double [^String s]
  (Double/parseDouble (str/trim s)))

(defn read-hourly-shares-24
  "Returns length-24 vector of shares (sum ≈ 1). Path: hourly_arrival_profile_from_sample.csv"
  [csv-path]
  (with-open [r (io/reader csv-path)]
    (let [rows (csv/read-csv r)
          header (mapv str/trim (first rows))
          idx-hour (.indexOf header "hour")
          idx-share (.indexOf header "arrival_share")
          _ (when (neg? idx-hour) (throw (ex-info "CSV missing 'hour' column" {:path csv-path})))
          share-col (if (neg? idx-share) 2 idx-share)
          by-hour (reduce (fn [acc row]
                            (let [h (Integer/parseInt (str/trim (nth row idx-hour)))
                                  sh (read-double (nth row share-col))]
                              (assoc acc h sh)))
                          {}
                          (rest rows))]
      (mapv #(double (get by-hour % 0.0)) (range 24)))))

(defn normalize-shares
  [shares]
  (let [s (reduce + 0.0 shares)]
    (if (pos? s)
      (mapv #(/ (double %) s) shares)
      (mapv (constantly (/ 1.0 24.0)) (range 24)))))

(defn shares->weekly-lambdas
  "For each simulation hour h, λ_h = daily-mean-arrivals * share[h mod 24].
   daily-mean-arrivals = sum of expected arrivals in one 24h day (e.g. 8 * 24 = 192 if 8/h flat)."
  [shares horizon daily-mean-arrivals]
  (let [sh (normalize-shares shares)]
    (mapv (fn [h]
            (* (double daily-mean-arrivals) (double (nth sh (mod h 24)))))
          (range horizon))))

(defn attach-real-hourly-profile
  "Merges into scenario: :hourly-arrival-rates (length horizon), updated :scenario-id suffix."
  [scenario csv-path]
  (let [shares (read-hourly-shares-24 csv-path)
        horizon (long (:horizon-hours scenario))
        daily-mean (* (double (:base-arrival-rate-per-hour scenario)) 24.0)
        rates (vec (shares->weekly-lambdas shares horizon daily-mean))]
    (-> scenario
        (assoc :hourly-arrival-rates rates)
        (assoc :scenario-id (str (:scenario-id scenario) "-real-hourly")))))

(comment
  "================================================================================
  FILE: data/real_profile.clj
  NAMESPACE: er-staffing.data.real-profile

  PURPOSE
    Read preprocessed **hospital hourly arrival shares** (24 values) from CSV,
    build full-horizon Poisson λ vector matching scenario :horizon-hours and
    baseline daily arrival total; attach to scenario for realistic pattern.

  INPUTS
    Scenario map, path to hourly_arrival_profile_from_sample.csv (from Python script).

  OUTPUTS
    Augmented scenario with :hourly-arrival-rates and updated :scenario-id.
  ================================================================================")
