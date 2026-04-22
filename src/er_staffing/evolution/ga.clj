(ns er-staffing.evolution.ga
  (:require [er-staffing.evolution.evaluation :as eva]
            [er-staffing.evolution.operators :as ops]
            [er-staffing.evolution.selection :as sel]
            [er-staffing.random :as rng]
            [er-staffing.staffing-v2 :as sv2]))

(defn- evaluate-all
  [staffings scenarios opts]
  (mapv (fn [st]
          {:staffing st
           :errors (vec (eva/case-errors scenarios st opts))})
        staffings))

(defn- finalize-offspring
  [m0 scenarios bounds budget-repair?]
  (let [m (if (pos? (sv2/total-staff m0))
            m0
            (assoc m0 :physician 1 :nurse 1))]
    (if budget-repair?
      (ops/repair-staffing-budget m scenarios bounds)
      m)))

(defn- breed-children
  [evaluated rng n-children bounds crossover-rate mutation-rate selection scenarios budget-repair?]
  (loop [ch []]
    (if (>= (count ch) n-children)
      (vec (take n-children ch))
      (let [p-a (sel/pick-parent evaluated rng {:selection selection :tournament-size 3})
            p-b (sel/pick-parent evaluated rng {:selection selection :tournament-size 3})
            sa (:staffing p-a)
            sb (:staffing p-b)
            raw (if (< (rng/rand-double rng) crossover-rate)
                  (ops/uniform-crossover sa sb rng)
                  sa)
            m-raw (ops/mutate raw rng bounds mutation-rate)
            mut (finalize-offspring m-raw scenarios bounds budget-repair?)]
        (recur (conj ch mut))))))

(defn- next-population-standard
  [evaluated sorted elite population-size rng bounds crossover-rate mutation-rate selection scenarios budget-repair?]
  (let [elites (mapv :staffing (take elite sorted))
        n-children (- population-size elite)
        children (breed-children evaluated rng n-children bounds crossover-rate mutation-rate selection scenarios budget-repair?)]
    (into elites children)))

(defn- next-population-lexicase-full
  [evaluated rng population-size mutation-rate bounds scenarios budget-repair?]
  (let [survivors (sel/lexicase-select-n-without-replacement evaluated rng population-size)
        mut-rate (* 0.5 (double mutation-rate))]
    (mapv (fn [ev]
            (let [s (:staffing ev)
                  m0 (ops/mutate s rng bounds mut-rate)]
              (finalize-offspring m0 scenarios bounds budget-repair?)))
          survivors)))

(defn run-ga
  "Returns {:best-staffing :best-errors :mean-error-best :history :final-evaluated}.

   `:budget-repair?` (default true): when true, project offspring onto feasible daily payroll
   w.r.t. `scenarios` (see `operators/repair-staffing-budget`). Set false for an unconstrained
   ablation — expect +10000 fitness terms when over cap.

   `:selection` options:
   - `:lexicase` / `:tournament` — **parent selection** only; offspring = crossover + mutate.
   - `:lexicase-full` — **environmental lexicase**: next generation = `population-size` survivors
     from repeated lexicase **without replacement**, then light mutation (half `mutation-rate`).
     No crossover; `elite` and `crossover-rate` unused in this mode."
  [{:keys [scenarios bounds population-size generations mutation-rate crossover-rate
           seed selection elite replications budget-repair?]
    :or {mutation-rate 0.25 crossover-rate 0.9 seed 42 selection :lexicase
         elite 2 replications 2 budget-repair? true}}]
  (let [rng (rng/make-rng seed)
        budget-repair? (not (false? budget-repair?))
        opts {:replications replications :seed seed}
        pop0 (vec (repeatedly population-size
                              #(finalize-offspring (ops/random-staffing rng bounds)
                                                    scenarios bounds budget-repair?)))]
    (loop [g 0
           population pop0
           history []]
      (let [evaluated (evaluate-all population scenarios opts)
            sorted (sort-by #(eva/mean-error (:errors %)) evaluated)
            best (first sorted)
            mean-best (eva/mean-error (:errors best))
            h (conj history {:generation g
                             :best-mean-error mean-best
                             :best-staffing (:staffing best)})]
        (if (>= g generations)
          {:best-staffing (:staffing best)
           :best-errors (:errors best)
           :mean-error-best mean-best
           :history h
           :final-evaluated evaluated}
          (recur (inc g)
                 (if (= selection :lexicase-full)
                   (next-population-lexicase-full evaluated rng population-size mutation-rate bounds scenarios budget-repair?)
                   (next-population-standard evaluated sorted elite population-size rng bounds
                                             crossover-rate mutation-rate selection scenarios budget-repair?))
                 h))))))

(comment
  "================================================================================
  FILE: evolution/ga.clj
  NAMESPACE: er-staffing.evolution.ga

  PURPOSE
    Main **genetic algorithm loop**: evaluate population (multi-scenario errors),
    sort by mean error, produce next generation via elite + crossover/mutation
    (or lexicase-full mode). Applies optional **budget repair** after random init
    and each offspring so genomes respect min daily cap across `scenarios`.

  INPUTS (run-ga option map)
    :scenarios — vector of scenario maps
    :bounds — per-role [lo hi] genome
    :population-size, :generations, :mutation-rate, :crossover-rate, :elite
    :seed, :replications (passed to week/run-week in evaluation)
    :selection — :lexicase | :tournament | :lexicase-full
    :budget-repair? — default true; false = unconstrained ablation

  OUTPUTS
    {:best-staffing :best-errors :mean-error-best :history :final-evaluated}

  DEPENDS ON
    evolution.evaluation (case-errors), evolution.operators, evolution.selection
  ================================================================================")
