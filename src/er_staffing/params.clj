(ns er-staffing.params
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def default-scenario "data/synthetic/scenarios/base_8h.edn")

(defn load-scenario
  ([] (load-scenario default-scenario))
  ([path]
   (with-open [r (java.io.PushbackReader. (io/reader path))]
     (edn/read r))))

(defn staffing-cost
  [{:keys [shift-length-hours nurse-hourly-cost pit-hourly-cost]}
   {:keys [nurses pit-physicians]}]
  (* shift-length-hours
     (+ (* nurse-hourly-cost nurses)
        (* pit-hourly-cost pit-physicians))))

(defn within-budget?
  [scenario staffing]
  (<= (staffing-cost scenario staffing)
      (:budget-cap-per-shift scenario)))

(defn total-servers
  [{:keys [nurses pit-physicians]}]
  (+ nurses pit-physicians))

(defn staffing->server-roles
  [{:keys [nurses pit-physicians]}]
  (vec
   (concat
    (repeat nurses :nurse)
    (repeat pit-physicians :pit))))
