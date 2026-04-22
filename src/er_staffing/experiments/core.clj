(ns er-staffing.experiments.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [er-staffing.evolution.ga :as ga]
            [er-staffing.simulation.stage-week :as week]))

(defn load-ga-config
  []
  (with-open [r (java.io.PushbackReader. (io/reader "data/synthetic/v2/ga_config.edn"))]
    (edn/read r)))

(defn merge-ga-opts
  [cfg key]
  (merge (:ga-defaults cfg)
         (get cfg key {})))

(defn run-ga-experiment
  "Run GA; attach :label, :summary (first scenario, backward compatible), plus
   **cross-scenario wait metrics** on the best genome: re-simulate each scenario in
   `scenarios` and record :avg-wait-max, :avg-wait-mean, :avg-wait-real-hourly (if that
   scenario id is present), and :wait-by-scenario-id. This avoids misleading `0.0` rows
   when the first scenario is easy but real-hourly or high-demand is not."
  [label scenarios ga-opts]
  (let [ga-opts (assoc ga-opts :scenarios scenarios)
        res (ga/run-ga ga-opts)
        best (:best-staffing res)
        opts {:replications (:replications ga-opts) :seed (:seed ga-opts)}
        summaries (mapv #(week/run-week % best opts) scenarios)
        waits (mapv #(double (:avg-wait-time %)) summaries)
        qlens (mapv #(double (:avg-queue-length %)) summaries)
        tiss (mapv #(double (:avg-time-in-system %)) summaries)
        ids (mapv (comp str :scenario-id) scenarios)
        by-id (zipmap ids waits)
        q-by-id (zipmap ids qlens)
        real-hr-id (first (filter #(str/ends-with? % "-real-hourly") ids))
        first-summ (first summaries)
        n (count waits)]
    (-> res
        (assoc :label label
               :summary first-summ
               :scenario-summaries summaries
               :avg-wait-max (apply max waits)
               :avg-wait-mean (/ (reduce + 0.0 waits) n)
               :wait-by-scenario-id by-id
               :avg-wait-real-hourly (when real-hr-id (get by-id real-hr-id))
               ;; Often > 0 when avg-wait rounds to 0; useful for comparisons.
               :avg-queue-len-max (apply max qlens)
               :avg-queue-len-mean (/ (reduce + 0.0 qlens) n)
               :avg-queue-len-real-hourly (when real-hr-id (get q-by-id real-hr-id))
               :avg-time-in-system-max (apply max tiss)
               :avg-time-in-system-mean (/ (reduce + 0.0 tiss) n)))))

(comment
  "================================================================================
  FILE: experiments/core.clj
  NAMESPACE: er-staffing.experiments.core

  PURPOSE
    Shared helpers for GA experiment scripts: load genome bounds from EDN, merge GA
    option maps, and run one labeled GA experiment with rich reporting.

  INPUTS
    - data/synthetic/v2/ga_config.edn  (genome bounds + default GA hyperparameters)
    - `merge-ga-opts cfg :ga-defaults` or `:ga-fast` — keyword selects EDN subsection
    - `run-ga-experiment label scenarios ga-opts` — `scenarios` is a vector of scenario
      maps (from scenario-v2 loaders); `ga-opts` is passed to `evolution.ga/run-ga`
      (must include :bounds, :population-size, :generations, etc.)

  BEHAVIOR
    - `run-ga-experiment` runs the genetic algorithm, then **re-simulates** the best
      staffing on **every** scenario in the vector. That yields max/mean wait times across
      worlds (not only the first scenario), which avoids first-scenario bias.

  OUTPUTS
    - Return value is `ga/run-ga`’s map plus :label, :summary (first scenario), cross-scenario
      :avg-wait-*, :avg-queue-len-*, :avg-time-in-system-* (max/mean/real-hourly where applicable).
      Queue / time-in-system help when average wait rounds to 0 under feasible staffing.

  USED BY
    - er-staffing.experiments.evolution-run
    - er-staffing.experiments.presentation-run
  ================================================================================")
