(ns er-staffing.simulation.stage-week
  "One-week DES: single FIFO queue, 7-role staffing, condition-tagged patients,
   specialist-first routing (see simulation.routing)."
  (:require [er-staffing.arrivals :as arrivals]
            [er-staffing.random :as rng]
            [er-staffing.scenario-v2 :as scen]
            [er-staffing.staffing-v2 :as sv2]
            [er-staffing.simulation.conditions :as conditions]
            [er-staffing.simulation.metrics-week :as mweek]
            [er-staffing.simulation.random-events :as rand-ev]
            [er-staffing.simulation.routing :as routing]))

(defn- initial-servers
  [staffing]
  (->> (sv2/staffing->server-roles staffing)
       (map-indexed (fn [idx role]
                      {:id idx :role role :busy? false
                       :busy-until Double/POSITIVE_INFINITY
                       :busy-time 0.0}))
       vec))

(defn- next-departure-index
  [servers]
  (when (some :busy? servers)
    (apply min-key #(get-in servers [% :busy-until])
           (keep-indexed (fn [idx s] (when (:busy? s) idx)) servers))))

(defn- drain-queue-at-time
  "Start as many services as possible from queue head at simulation time t."
  [rng scenario horizon t queue servers service-records total-wait]
  (loop [q queue s servers recs service-records tw total-wait]
    (if (empty? q)
      {:queue q :servers s :service-records recs :total-wait tw}
      (let [head (first q)
            wait (- t (:arrival head))
            ch (routing/choose-server scenario s (:condition head) wait (count q))]
        (if-not ch
          {:queue q :servers s :service-records recs :total-wait tw}
          (let [idx (:server-idx ch)
                sk (:service-kind ch)
                role (routing/role-at s idx)
                rate (routing/effective-service-rate (:providers scenario)
                                                     (:rate-multipliers scenario)
                                                     role (:condition head))
                dur (rng/sample-exponential rng rate)
                end (+ t dur)
                h (double horizon)
                busy-in (max 0.0 (- (min end h) t))
                srv (nth s idx)
                srv' (-> srv
                        (assoc :busy? true)
                        (assoc :busy-until end)
                        (update :busy-time + busy-in))
                s' (assoc s idx srv')
                rec {:arrival-time (:arrival head)
                     :service-start-time t
                     :service-end-time end
                     :condition (:condition head)
                     :server-idx idx
                     :service-kind sk
                     :role role
                     :wait-time wait}]
            (recur (vec (rest q)) s' (conj recs rec) (+ tw wait))))))))

(defn- utilization-by-role
  [servers staffing horizon]
  (into {}
        (for [role sv2/role-order
              :let [cnt (long (get staffing role 0))
                    avail (* (double horizon) (double cnt))
                    busy (reduce + 0.0
                                 (for [s servers :when (= role (:role s))]
                                   (:busy-time s)))]]
          [role (if (pos? avail) (/ busy avail) 0.0)])))

(defn run-one-week
  [scenario staffing {:keys [seed record-event-log?] :or {seed 42 record-event-log? false}}]
  (when (zero? (sv2/total-staff staffing))
    (throw (ex-info "Staffing must have at least one person across roles." {:staffing staffing})))
  (let [rng-init (rng/make-rng (+ (long seed) 31))
        scenario (rand-ev/realize scenario rng-init)
        rng (rng/make-rng seed)
        horizon (double (:horizon-hours scenario))
        rates (conditions/hourly-arrival-rates-vector scenario)
        future-arrivals (arrivals/piecewise-hourly-arrivals rng rates (long horizon))
        servers0 (initial-servers staffing)]
    (loop [current-time 0.0
           future-arrivals future-arrivals
           waiting-queue []
           servers servers0
           patients-arrived 0
           patients-completed 0
           total-wait 0.0
           total-system 0.0
           queue-area 0.0
           last-event-time 0.0
           service-records []
           uid-counter 0
           n-match 0
           n-gen 0
           n-mis 0
           event-log []]
      (let [arrival-time (first future-arrivals)
            dep-idx (next-departure-index servers)
            dep-time (when dep-idx (get-in servers [dep-idx :busy-until]))]
        (cond
          (and (nil? arrival-time) (nil? dep-idx))
          (let [h horizon
                completed (double patients-completed)
                spec-rate (if (pos? completed) (/ (double n-match) completed) 0.0)]
            {:avg-wait-time (if (pos? completed) (/ total-wait completed) 0.0)
             :avg-time-in-system (if (pos? completed) (/ total-system completed) 0.0)
             :avg-queue-length (if (pos? h) (/ queue-area h) 0.0)
             :specialty-matching-rate spec-rate
             :patients-arrived (double patients-arrived)
             :patients-completed completed
             :utilization-by-role (utilization-by-role servers staffing h)
             :service-records service-records
             :event-log (when record-event-log? event-log)
             :n-specialist-match n-match
             :n-generalist n-gen
             :n-specialist-mismatch n-mis})

          (or (and arrival-time (nil? dep-time))
              (and arrival-time dep-time (< arrival-time dep-time)))
          (let [event-time arrival-time
                c (conditions/sample-condition rng scenario event-time)
                queue-area' (+ queue-area (* (count waiting-queue)
                                             (- (min event-time horizon)
                                                (min last-event-time horizon))))
                uid-n (inc uid-counter)
                p {:uid uid-n :arrival event-time :condition c}
                q0 (conj waiting-queue p)
                dq (drain-queue-at-time rng scenario horizon event-time q0 servers service-records total-wait)]
            (recur event-time
                   (next future-arrivals)
                   (:queue dq)
                   (:servers dq)
                   (inc patients-arrived)
                   patients-completed
                   (:total-wait dq)
                   total-system
                   queue-area'
                   event-time
                   (:service-records dq)
                   uid-n
                   n-match
                   n-gen
                   n-mis
                   event-log))

          :else
          (let [event-time dep-time
                h horizon
                queue-area' (+ queue-area (* (count waiting-queue)
                                             (- (min event-time h)
                                                (min last-event-time h))))
                completed-rec-idx
                (first
                 (keep-indexed
                  (fn [idx rec]
                    (when (and (nil? (:closed? rec))
                               (= dep-idx (:server-idx rec))
                               (= dep-time (:service-end-time rec)))
                      idx))
                  service-records))
                rec (when completed-rec-idx (nth service-records completed-rec-idx))
                _ (when (nil? completed-rec-idx)
                    (throw (ex-info "Internal: departure with no open record"
                                    {:dep-idx dep-idx :dep-time dep-time})))
                total-system' (+ total-system (- dep-time (:arrival-time rec)))
                sk (:service-kind rec)
                [n-match' n-gen' n-mis']
                (case sk
                  :specialist-match [(inc n-match) n-gen n-mis]
                  :generalist [n-match (inc n-gen) n-mis]
                  :specialist-mismatch [n-match n-gen (inc n-mis)]
                  [n-match n-gen n-mis])
                freed (assoc (nth servers dep-idx) :busy? false :busy-until Double/POSITIVE_INFINITY)
                servers-f (assoc servers dep-idx freed)
                recs0 (assoc service-records completed-rec-idx (assoc rec :closed? true))
                dq (drain-queue-at-time rng scenario h event-time waiting-queue servers-f recs0 total-wait)]
            (recur event-time
                   future-arrivals
                   (:queue dq)
                   (:servers dq)
                   patients-arrived
                   (inc patients-completed)
                   (:total-wait dq)
                   total-system'
                   queue-area'
                   event-time
                   (:service-records dq)
                   uid-counter
                   n-match'
                   n-gen'
                   n-mis'
                   event-log)))))))

(defn run-week
  [scenario staffing {:keys [replications seed record-event-log?]
                      :or {replications 10 seed 42 record-event-log? false}}]
  (let [summaries
        (vec
         (for [rep (range replications)]
           (run-one-week scenario staffing
                         {:seed (+ seed rep)
                          :record-event-log? record-event-log?})))]
    (merge
     {:scenario-id (:scenario-id scenario)
      :staffing staffing
      :replications replications
      :daily-staffing-cost (sv2/daily-staffing-cost scenario staffing)
      :within-budget? (sv2/within-daily-budget? scenario staffing)}
     (mweek/summarize-week-replications summaries)
     {:utilization-by-role (mweek/utilization-by-role-mean summaries)})))

(defn demo
  []
  (let [scenario (scen/load-week-scenario)
        staffing {:rt 1 :cardio 1 :radio 1 :mh 1 :physician 2 :pa 2 :nurse 4}]
    (run-week scenario staffing {:replications 3 :seed 7})))

(comment
  "================================================================================
  FILE: simulation/stage_week.clj
  NAMESPACE: er-staffing.simulation.stage-week

  PURPOSE
    **168-hour** discrete-event style week simulation: Poisson arrivals (hourly λ),
    routing to role queues, service with configurable rates, optional fixed or
    random events. `run-week` averages metrics across `:replications` seeded runs.

  INPUTS
    scenario map (from EDN + optional real profile), staffing map, opts
    {:replications :seed :record-event-log?}.

  OUTPUTS
    Summary map: avg wait, time-in-system, queue length, specialty match rate,
    budget flags, utilization — fed into evaluation.fitness/score-summary.

  NOTE
    Large file; core entry point for experiments is `run-week`.
  ================================================================================")
