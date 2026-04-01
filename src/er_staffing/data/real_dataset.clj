(ns er-staffing.data.real-dataset
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(def cleaned-csv "data/processed/real/hospital_wait_sample_cleaned.csv")
(def hourly-profile-csv "data/processed/real/hourly_arrival_profile_from_sample.csv")

(defn read-csv
  [path]
  (with-open [reader (io/reader path)]
    (doall (csv/read-csv reader))))

(defn load-hourly-profile
  []
  (let [[header & rows] (read-csv hourly-profile-csv)
        by-col (zipmap header (apply map vector rows))]
    (mapv #(Double/parseDouble %)
          (get by-col "arrival_count"))))

(defn load-cleaned-preview
  [n]
  (take n (read-csv cleaned-csv)))
