(ns er-staffing.experiments.presentation-run
  "Presentation experiment bundle: baseline staffing vs GA variants (lexicase, tournament),
   optional stronger 5-case run. Writes `results/PRESENTATION_BUNDLE.md` and
   `results/presentation_runs.csv`.

   Usage:
     clojure -M:pres           # full (can take many minutes)
     clojure -M:pres -- --quick"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [er-staffing.evaluation.fitness :as fitness]
            [er-staffing.experiments.core :as ex]
            [er-staffing.experiments.scenarios :as scen]
            [er-staffing.simulation.stage-week :as week]
            [er-staffing.staffing-v2 :as sv2]))

(def hand-picked-staffing
  "Same as `er-staffing.main` / demo; used as a fixed baseline."
  {:rt 1 :cardio 1 :radio 1 :mh 1 :physician 2 :pa 2 :nurse 4})

(defn- summarize-staffing-across
  [label staffing scenarios {:keys [replications seed] :or {replications 2 seed 42}}]
  (let [opts {:replications replications :seed seed}
        rows (for [sc scenarios]
               (let [cost (sv2/daily-staffing-cost sc staffing)
                     cap (:daily-budget-cap sc)
                     summ (week/run-week sc staffing opts)
                     err (fitness/score-summary sc staffing summ)]
                 {:label label
                  :staffing staffing
                  :scenario-id (str (:scenario-id sc))
                  :daily-cost cost
                  :budget-cap cap
                  :within-budget? (sv2/within-daily-budget? sc staffing)
                  :avg-wait (:avg-wait-time summ)
                  :match-rate (:specialty-matching-rate summ)
                  :fitness err}))]
    rows))

(defn- fmt-row-md
  [r]
  (format "| %s | %s | %.0f / %.0f | %s | %.4f | %.4f | %.4f |"
          (:label r)
          (:scenario-id r)
          (:daily-cost r)
          (:budget-cap r)
          (if (:within-budget? r) "yes" "no")
          (:avg-wait r)
          (:match-rate r)
          (:fitness r)))

(defn- ga-row-md
  [r scenario-labels]
  (format "| %s | %s | %.6f | %.6f | %.6f | %.6f | %s | %.4f | %s |"
          (:label r)
          scenario-labels
          (double (:mean-error-best r))
          (double (get-in r [:summary :avg-wait-time] 0))
          (double (:avg-wait-max r 0))
          (double (:avg-wait-mean r 0))
          (if-let [rh (:avg-wait-real-hourly r)]
            (format "%.6f" (double rh))
            "—")
          (double (get-in r [:summary :specialty-matching-rate] 0))
          (str (get-in r [:summary :within-budget?]))))

(defn- write-md!
  [path hand-rows ga-rows five-detail quick?]
  (io/make-parents path)
  (spit path
        (str "# Presentation experiment bundle\n\n"
             (if quick?
               "_Quick mode (`--quick`): smaller GA; use full run for final numbers._\n\n"
               "_Full search budget — suitable for screenshots / final report._\n\n")
             "## 1. How to read fitness\n\n"
             "- Scalar **fitness** = weighted waits/queues + specialty mismatch + **10000** if daily cost exceeds that scenario’s **budget cap**.\n"
             "- **Mean error** in GA rows = average of per-scenario errors (lexicase uses the full error vector).\n"
             "- **Budget repair:** Each GA genome is adjusted with **`repair-staffing-budget`** so daily payroll ≤ the **minimum** cap across the scenarios in that run (when tight-budget is in the set, **48k** binds). Comparisons focus on **operational** quality, not runaway staffing.\n"
             "- **Ablation row** (`*-no-budget-repair*`): same GA **without** repair — often **over budget** with fitness ~**10000** + small terms; use as **unconstrained vs constrained** contrast.\n"
             "- **Wait columns:** **1st** = wait on the **first** scenario only (often **≈0** after budget repair — feasible staffing clears waits). **Max / mean** = over **all** scenarios in that run.\n"
             "- **Queue / time-in-system** (second table + extra CSV columns): often **non-zero** even when average wait rounds to 0.\n\n"
             "## 2. Hand-picked staffing (demo baseline)\n\n"
             "Same staffing as `er-staffing.main` demo — not tuned by search.\n\n"
             "| Label | Scenario | Daily cost / cap | In budget? | Avg wait | Match rate | Fitness |\n"
             "|-------|----------|------------------|------------|----------|------------|---------|\n"
             (str/join "\n" (map fmt-row-md hand-rows))
             "\n\n"
             "## 3. Genetic algorithm comparisons\n\n"
             "| Run | Scenarios | Mean error | Wait 1st | Wait max | Wait mean | Wait real-hr | Match (1st) | Budget (1st)? |\n"
             "|-----|-----------|------------|----------|----------|-----------|--------------|---------------|----------------|\n"
             (str/join "\n" (map (fn [[r sl]] (ga-row-md r sl)) ga-rows))
             "\n\n### Same runs — queue length & time in system (max/mean over scenarios)\n\n"
             "Use when **wait** columns look like 0 — constrained budgeting still allows queue / flow metrics to differ.\n\n"
             "| Run | Queue len max | Queue len mean | Time-in-system max | Time-in-system mean |\n"
             "|-----|---------------|----------------|---------------------|----------------------|\n"
             (str/join "\n"
                       (map (fn [[r _sl]]
                              (format "| %s | %.6f | %.6f | %.6f | %.6f |"
                                      (:label r)
                                      (double (:avg-queue-len-max r 0))
                                      (double (:avg-queue-len-mean r 0))
                                      (double (:avg-time-in-system-max r 0))
                                      (double (:avg-time-in-system-mean r 0))))
                            ga-rows))
             (when five-detail
               (str "\n\n### 5-case lexicase — per-scenario errors (best genome)\n\n"
                    "| Scenario | Error |\n"
                    "|----------|-------|\n"
                    (str/join "\n"
                              (map (fn [[id e]]
                                     (format "| %s | %.6f |" id (double e)))
                                   (map vector (:five-case-ids five-detail) (:best-errors five-detail))))))
             "\n\n"
             "## 4. Summary bullets\n\n"
             "- **Budget-aware evolution:** Default runs use **repair** so rows compare **operational** scores on fundable staffing.\n"
             "- **Ablation (`A-ablation-*`):** **No repair** — expect **~10000** mean error when the GA staffs up to cut waits; contrasts **fiscal feasibility** vs raw queue minimization.\n"
             "- **2-case (synthetic + real hourly):** Compare **lexicase** vs **tournament** on **mean error** and **match rate** (`B` vs `C`).\n"
             "- **5-case (`E`):** Harder joint objective than 2-case; mean error often **higher** than `B`/`C`.\n"
             "- Chart from **`presentation_ga_runs.csv`** and hand baseline from **`presentation_hand_baseline.csv`**.\n")))

(defn- write-csv! [path rows]
  (with-open [w (io/writer path)]
    (csv/write-csv w
                     (cons ["label" "scenario_id" "daily_cost" "budget_cap" "within_budget"
                            "avg_wait" "match_rate" "fitness"]
                           (map (fn [r]
                                  [(str (:label r))
                                   (str (:scenario-id r))
                                   (str (:daily-cost r))
                                   (str (:budget-cap r))
                                   (str (:within-budget? r))
                                   (str (:avg-wait r))
                                   (str (:match-rate r))
                                   (str (:fitness r))])
                                rows)))))

(defn- fmt-wait-csv
  [x]
  (if (nil? x)
    ""
    (format "%.8f" (double x))))

(defn- write-ga-csv! [path ga-rows]
  (with-open [w (io/writer path)]
    (csv/write-csv w
                     (cons ["label" "scenarios" "mean_error" "avg_wait_first" "avg_wait_max"
                            "avg_wait_mean" "avg_wait_real_hourly"
                            "avg_queue_len_max" "avg_queue_len_mean" "avg_queue_len_real_hourly"
                            "avg_time_in_system_max" "avg_time_in_system_mean"
                            "match_rate_first" "within_budget_first"
                            "rt" "cardio" "radio" "mh" "physician" "pa" "nurse"]
                           (map (fn [[r _sl]]
                                  (let [b (:best-staffing r)
                                        s (:summary r)]
                                    [(str (:label r))
                                     (str (:scenario-labels r))
                                     (str (:mean-error-best r))
                                     (fmt-wait-csv (get s :avg-wait-time))
                                     (fmt-wait-csv (:avg-wait-max r))
                                     (fmt-wait-csv (:avg-wait-mean r))
                                     (fmt-wait-csv (:avg-wait-real-hourly r))
                                     (fmt-wait-csv (:avg-queue-len-max r))
                                     (fmt-wait-csv (:avg-queue-len-mean r))
                                     (fmt-wait-csv (:avg-queue-len-real-hourly r))
                                     (fmt-wait-csv (:avg-time-in-system-max r))
                                     (fmt-wait-csv (:avg-time-in-system-mean r))
                                     (str (get s :specialty-matching-rate))
                                     (str (get s :within-budget?))
                                     (str (:rt b)) (str (:cardio b)) (str (:radio b)) (str (:mh b))
                                     (str (:physician b)) (str (:pa b)) (str (:nurse b))]))
                                ga-rows)))))

(defn -main
  [& args]
  (let [quick? (boolean (some #{"--quick" "-q"} args))
        cfg (ex/load-ga-config)
        base (merge (ex/merge-ga-opts cfg :ga-defaults)
                    {:bounds (:genome-bounds cfg)
                     :replications 3
                     :seed 42
                     :selection :lexicase})
        syn (scen/week-v1-for-presentation)
        real (scen/week-v1-real-hourly-for-presentation)
        syn-rand (scen/with-random-events syn)
        five (scen/lexicase-five-vector-for-presentation)
        ;; Quick mode shrinks A/D/E; B/C keep full 2-case budget so lexicase vs tournament
        ;; stays comparable to `clojure -M:pres` (otherwise quick falsely “fails” lexicase).
        [p1 g1] (if quick? [8 4] [16 8])
        [p2 g2] [16 (if quick? 8 10)]
        [p5 g5] (if quick? [6 4] [12 10])
        five-seed 43
        hand-scenarios five
        hand-rows (summarize-staffing-across "hand-picked (main demo)" hand-picked-staffing hand-scenarios
                                             {:replications 3 :seed 42})
        r0 (ex/run-ga-experiment "A-ablation-synthetic-no-budget-repair" [syn]
                                  (merge base {:population-size p1 :generations g1 :seed 42
                                               :budget-repair? false}))
        r1 (ex/run-ga-experiment "A-ga-synthetic-only" [syn]
                                 (merge base {:population-size p1 :generations g1 :seed 42}))
        r2 (ex/run-ga-experiment "B-ga-lexicase-2-case" [syn real]
                                 (merge base {:population-size p2 :generations g2 :seed 42}))
        r3 (ex/run-ga-experiment "C-ga-tournament-2-case" [syn real]
                                 (merge base {:population-size p2 :generations g2 :seed 44
                                              :selection :tournament :tournament-size 3}))
        r4 (ex/run-ga-experiment "D-ga-synthetic-random-events" [syn-rand]
                                 (merge base {:population-size p1 :generations g1 :seed 45}))
        r5 (ex/run-ga-experiment "E-ga-lexicase-5-case" five
                                 (merge base {:population-size p5 :generations g5 :seed five-seed}))
        five-detail (assoc r5 :five-case-ids (mapv #(str (:scenario-id %)) five))
        ga-rows [[(assoc r0 :scenario-labels "week-v1-pres") "week-v1-pres (ablation)"]
                 [(assoc r1 :scenario-labels "week-v1-pres") "week-v1-pres"]
                 [(assoc r2 :scenario-labels "week-v1-pres+real-hourly") "week-v1-pres+real-hourly"]
                 [(assoc r3 :scenario-labels "week-v1-pres+real-hourly") "week-v1-pres+real-hourly"]
                 [(assoc r4 :scenario-labels "week-v1-pres-rand-events") "week-v1-pres-rand-events"]
                 [(assoc r5 :scenario-labels "5-case lexicase") "5-case lexicase"]]]
    (write-md! "results/PRESENTATION_BUNDLE.md" hand-rows ga-rows five-detail quick?)
    (write-csv! "results/presentation_hand_baseline.csv" hand-rows)
    (write-ga-csv! "results/presentation_ga_runs.csv" ga-rows)
    (spit "results/presentation_5case_detail.txt"
          (str "Best genome (5-case run):\n"
               (pr-str (:best-staffing r5))
               "\n\nPer-scenario errors:\n"
               (pr-str (:best-errors r5))
               "\n\nScenario ids:\n"
               (pr-str (:five-case-ids five-detail))))
    (with-open [w (io/writer "results/presentation_5case_errors.csv")]
      (csv/write-csv w
                     (cons ["scenario_id" "error_best_genome"]
                           (map vector
                                (:five-case-ids five-detail)
                                (map str (:best-errors r5))))))
    (println "Wrote results/PRESENTATION_BUNDLE.md")
    (println "     results/presentation_hand_baseline.csv")
    (println "     results/presentation_ga_runs.csv")
    (println "     results/presentation_5case_detail.txt")
    (println "     results/presentation_5case_errors.csv")
    (try
      (let [script (str (io/file (System/getProperty "user.dir") "scripts" "plot_presentation_figures.py"))
            {:keys [exit out err]} (sh/sh "python3" script)]
        (if (zero? exit)
          (do (when (seq out) (print out))
              (println "Wrote PNGs under results/figures/ (matplotlib)"))
          (println "Optional PNG plots skipped:" err)))
      (catch Exception e
        (println "Optional PNG plots: run  python3 scripts/plot_presentation_figures.py  —" (.getMessage e))))
    (doseq [[r _] ga-rows]
      (println (:label r) "mean-error" (:mean-error-best r) "staffing" (:best-staffing r)))))

(comment
  "================================================================================
  FILE: experiments/presentation_run.clj
  NAMESPACE: er-staffing.experiments.presentation-run

  PURPOSE
    Command-line entry (`clojure -M:pres`) that builds a **presentation bundle**:
    hand-picked staffing evaluated on five scenarios, several GA runs (ablation,
    synthetic-only, 2-case lexicase vs tournament, random-events, 5-case lexicase),
    Markdown + CSV outputs, optional Python figures.

  INPUTS
    - `data/synthetic/v2/ga_config.edn` — genome bounds and GA defaults
    - Scenario EDNs via `experiments.scenarios` (week-v1, real-hourly, cases, etc.)
    - CLI: `--quick` / `-q` for smaller population × generations on some runs

  OUTPUTS (under results/)
    - PRESENTATION_BUNDLE.md — tables + summary bullets
    - presentation_ga_runs.csv — includes mean_error, avg_wait_first, avg_wait_max,
      avg_wait_mean, avg_wait_real_hourly, match_rate, genome counts
    - presentation_hand_baseline.csv — hand staffing per scenario
    - presentation_5case_detail.txt — best genome + per-scenario errors for E run
    - results/figures/*.png — if `python3 scripts/plot_presentation_figures.py` succeeds

  KEY BEHAVIOR
    - Calls `experiments.core/run-ga-experiment` which attaches **max/mean wait**
      across all scenarios in each GA’s scenario vector (not only the first).
    - Uses `:replications 3` for slightly more stable stochastic summaries.
  ================================================================================")
