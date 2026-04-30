(ns er-staffing.experiments.model-dataset
  "Generate many single-scenario GA runs for surrogate-model training.

   Outputs:
   - Scenario EDNs under a chosen folder (for audit/replay)
   - One CSV with model inputs + GA best genome per scenario

   Usage examples:
     clojure -M:dataset
     clojure -M:dataset -- --count 120 --replications 5 --seed 42
     clojure -M:dataset -- --count 90 --selection tournament --replications 5"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [er-staffing.experiments.core :as ex]
            [er-staffing.random :as rng]
            [er-staffing.scenario-v2 :as scen]))

(def default-real-hourly-csv "data/processed/real/hourly_arrival_profile_from_sample.csv")
(def default-scenario-edn "data/synthetic/v2/week_scenario_presentation.edn")
(def default-scenario-dir "data/synthetic/v2/model_dataset")
(def default-output-csv "results/model_training_dataset.csv")

(def max-event-slots 6)

(def conditions-order [:respiratory :cardio :trauma :psych :generic])
(def role-order [:rt :cardio :radio :mh :physician :pa :nurse])

(defn- parse-kv-args
  [args]
  (loop [m {}
         xs args]
    (if (empty? xs)
      m
      (let [[k & remaining] xs]
        (cond
          (= "--" k) (recur m remaining)
          (and k (str/starts-with? k "--") (seq remaining))
          (recur (assoc m (subs k 2) (first remaining)) (rest remaining))
          :else
          (recur m remaining))))))

(defn- read-long
  [s default]
  (try (Long/parseLong (str s))
       (catch Exception _ default)))

(defn- normalize
  [xs]
  (let [s (reduce + 0.0 xs)]
    (if (pos? s)
      (mapv #(/ (double %) s) xs)
      (mapv (constantly (/ 1.0 (double (count xs)))) xs))))

(defn- random-shares
  [r n]
  ;; Simple positive-sample normalization; good enough for synthetic variety.
  (normalize (repeatedly n #(+ 0.05 (* 2.0 (rng/rand-double r))))))

(defn- jitter-hourly-shares
  [r base-shares]
  (->> base-shares
       (map (fn [x]
              (let [factor (+ 0.7 (* 0.6 (rng/rand-double r)))]
                (* (double x) factor))))
       vec
       normalize))

(defn- sample-condition-probs
  [r]
  (let [base (random-shares r 5)
        resp-boost (if (< (rng/rand-double r) 0.35) 1.4 1.0)
        trauma-boost (if (< (rng/rand-double r) 0.20) 1.5 1.0)
        adjusted [(* (nth base 0) resp-boost)
                  (nth base 1)
                  (* (nth base 2) trauma-boost)
                  (nth base 3)
                  (nth base 4)]
        p (normalize adjusted)]
    {:respiratory (nth p 0)
     :cardio (nth p 1)
     :trauma (nth p 2)
     :psych (nth p 3)
     :generic (nth p 4)}))

(def event-templates
  [{:condition-multipliers {:respiratory 2.0} :arrival-multiplier 1.00}
   {:condition-multipliers {:cardio 1.6} :arrival-multiplier 1.00}
   {:condition-multipliers {:trauma 2.6 :respiratory 1.2} :arrival-multiplier 1.08}
   {:condition-multipliers {:psych 2.2} :arrival-multiplier 1.00}
   {:condition-multipliers {:respiratory 4.5} :arrival-multiplier 1.12}
   {:condition-multipliers {:generic 1.4 :cardio 1.2} :arrival-multiplier 1.00}])

(defn- sample-events
  [r horizon]
  (let [n (rng/rand-int-between r 0 max-event-slots)]
    (vec
     (for [idx (range n)]
       (let [tpl (nth event-templates (mod idx (count event-templates)))
             dur (rng/rand-int-between r 6 36)
             start-max (max 0 (- (long horizon) dur 1))
             start (if (pos? start-max) (rng/rand-int-between r 0 start-max) 0)]
         {:id (keyword (str "ds-evt-" idx))
          :start-hour (double start)
          :duration (double dur)
          :arrival-multiplier (double (:arrival-multiplier tpl))
          :condition-multipliers (:condition-multipliers tpl)})))))

(defn- scenario->event-features
  [events]
  (mapcat
   (fn [j]
     (let [evt (nth events j nil)
           cm (or (:condition-multipliers evt) {})]
       [(if evt 1 0)
        (double (or (:start-hour evt) 0.0))
        (double (or (:duration evt) 0.0))
        (double (or (:arrival-multiplier evt) 1.0))
        (double (get cm :respiratory 1.0))
        (double (get cm :cardio 1.0))
        (double (get cm :trauma 1.0))
        (double (get cm :psych 1.0))
        (double (get cm :generic 1.0))]))
   (range max-event-slots)))

(defn- scenario->feature-row
  [{:keys [scenario-id base-arrival-rate-per-hour routing condition-probs-baseline daily-budget-cap events]}
   arrival-profile-type
   shares24]
  (let [base-cols [(str scenario-id)
                   (format "%.8f" (double base-arrival-rate-per-hour))
                   (str (long (or (:queue-threshold routing) 12)))
                   (format "%.8f" (double (or (:wait-threshold-hours routing) 1.5)))
                   (format "%.8f" (double (get condition-probs-baseline :respiratory 0.0)))
                   (format "%.8f" (double (get condition-probs-baseline :cardio 0.0)))
                   (format "%.8f" (double (get condition-probs-baseline :trauma 0.0)))
                   (format "%.8f" (double (get condition-probs-baseline :psych 0.0)))
                   (format "%.8f" (double (get condition-probs-baseline :generic 0.0)))
                   arrival-profile-type
                   (format "%.8f" (double daily-budget-cap))]
        share-cols (mapv #(format "%.8f" (double %)) shares24)
        event-cols (mapv str (scenario->event-features events))]
    (vec (concat base-cols share-cols event-cols))))

(defn- dataset-header
  []
  (vec
   (concat
    ["scenario_id"
     "base_arrival_rate_per_hour"
     "queue_threshold"
     "wait_threshold_hours"
     "p_respiratory"
     "p_cardio"
     "p_trauma"
     "p_psych"
     "p_generic"
     "arrival_profile_type"
     "daily_budget_cap"]
    (for [h (range 24)] (format "arr_share_h%02d" h))
    (mapcat (fn [j]
              [(format "evt%d_present" j)
               (format "evt%d_start_hour" j)
               (format "evt%d_duration" j)
               (format "evt%d_arrival_multiplier" j)
               (format "evt%d_mult_respiratory" j)
               (format "evt%d_mult_cardio" j)
               (format "evt%d_mult_trauma" j)
               (format "evt%d_mult_psych" j)
               (format "evt%d_mult_generic" j)])
            (range 1 (inc max-event-slots)))
    ["ga_selection"
     "ga_seed"
     "ga_replications"
     "ga_population_size"
     "ga_generations"
     "mean_error_best"
     "best_rt"
     "best_cardio"
     "best_radio"
     "best_mh"
     "best_physician"
     "best_pa"
     "best_nurse"])))

(defn- choose-arrival-profile
  [r real-shares]
  (if (< (rng/rand-double r) 0.5)
    {:type "flat"
     :shares (vec (repeat 24 (/ 1.0 24.0)))
     :use-hourly? false}
    {:type "real_hourly"
     :shares (jitter-hourly-shares r real-shares)
     :use-hourly? true}))

(defn- build-scenario
  [base r idx real-shares]
  (let [{:keys [type shares use-hourly?]} (choose-arrival-profile r real-shares)
        sid (format "ds-%04d-%s" idx (if (= type "flat") "flat" "real"))
        horizon (:horizon-hours base)
        queue-th (rng/rand-int-between r 8 18)
        wait-th (+ 0.75 (* 1.5 (rng/rand-double r)))
        base-lambda (+ 10.0 (* 25.0 (rng/rand-double r)))
        cprobs (sample-condition-probs r)
        cap (+ 45000.0 (* 90000.0 (rng/rand-double r)))
        events (sample-events r horizon)
        daily-mean (* base-lambda 24.0)
        hourly-rates (when use-hourly?
                       (mapv #(* daily-mean (double %)) shares))
        s (cond-> (assoc base
                    :scenario-id sid
                    :base-arrival-rate-per-hour base-lambda
                    :routing {:queue-threshold queue-th
                              :wait-threshold-hours wait-th}
                    :condition-probs-baseline cprobs
                    :daily-budget-cap cap
                    :events events)
            use-hourly? (assoc :hourly-arrival-rates hourly-rates))]
    {:scenario s :arrival-profile-type type :shares24 shares}))

(defn- write-scenario!
  [path scenario]
  (io/make-parents path)
  (with-open [w (io/writer path)]
    (binding [*out* w]
      (pp/pprint scenario))))

(defn- append-csv-row!
  [writer row]
  (csv/write-csv writer [row])
  (.flush writer))

(defn -main
  [& raw-args]
  (let [args (parse-kv-args raw-args)
        count-scenarios (read-long (get args "count") 60)
        seed0 (read-long (get args "seed") 42)
        replications (read-long (get args "replications") 5)
        population-size (read-long (get args "population") 16)
        generations (read-long (get args "generations") 8)
        selection (keyword (or (get args "selection") "lexicase"))
        scenario-dir (or (get args "scenario-dir") default-scenario-dir)
        out-csv (or (get args "out-csv") default-output-csv)
        base-scenario-path (or (get args "base-scenario") default-scenario-edn)
        real-csv (or (get args "real-csv") default-real-hourly-csv)
        base (scen/load-week-scenario base-scenario-path)
        real-shares (try
                      (let [read-hourly-shares-24 (requiring-resolve 'er-staffing.data.real-profile/read-hourly-shares-24)]
                        (read-hourly-shares-24 real-csv))
                      (catch Exception _
                        (vec (repeat 24 (/ 1.0 24.0)))))
        cfg (ex/load-ga-config)
        ga-base (merge (ex/merge-ga-opts cfg :ga-defaults)
                       {:bounds (:genome-bounds cfg)
                        :replications replications
                        :population-size population-size
                        :generations generations
                        :selection selection})
        rng0 (rng/make-rng seed0)]
    (io/make-parents out-csv)
    (with-open [w (io/writer out-csv)]
      (csv/write-csv w [(dataset-header)])
      (dotimes [i count-scenarios]
        (let [sample (build-scenario base rng0 (inc i) real-shares)
              scenario (:scenario sample)
              scenario-path (format "%s/scenario_%04d.edn" scenario-dir (inc i))
              ga-seed (+ seed0 i)
              ga-opts (assoc ga-base :seed ga-seed)
              result (ex/run-ga-experiment (str "dataset-" (inc i)) [scenario] ga-opts)
              best (:best-staffing result)
              x-row (scenario->feature-row scenario (:arrival-profile-type sample) (:shares24 sample))
              y-row [(name selection)
                     (str ga-seed)
                     (str replications)
                     (str population-size)
                     (str generations)
                     (format "%.8f" (double (:mean-error-best result)))
                     (str (get best :rt 0))
                     (str (get best :cardio 0))
                     (str (get best :radio 0))
                     (str (get best :mh 0))
                     (str (get best :physician 0))
                     (str (get best :pa 0))
                     (str (get best :nurse 0))]]
          (write-scenario! scenario-path scenario)
          (append-csv-row! w (vec (concat x-row y-row)))
          (println "Generated" scenario-path "-> best staffing" best "mean-error" (:mean-error-best result)))))
    (println "Wrote dataset CSV:" out-csv)
    (println "Wrote scenario files under:" scenario-dir)
    (println "Done. Use this CSV directly for surrogate-model training.")))
