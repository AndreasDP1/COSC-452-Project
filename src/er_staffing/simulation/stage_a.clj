(ns er-staffing.simulation.stage-a
  (:require [er-staffing.arrivals :as arrivals]
            [er-staffing.params :as params]
            [er-staffing.random :as rng]
            [er-staffing.simulation.metrics :as metrics]))

(defn- initial-servers
  [staffing]
  (->> (params/staffing->server-roles staffing)
       (map-indexed (fn [idx role]
                      {:id idx
                       :role role
                       :busy-until Double/POSITIVE_INFINITY
                       :busy-time 0.0
                       :busy? false}))
       vec))

(defn- service-rate-for-role
  [scenario role]
  (case role
    :nurse (:nurse-service-rate-per-hour scenario)
    :pit   (:pit-service-rate-per-hour scenario)
    (:nurse-service-rate-per-hour scenario)))

(defn- idle-server-index
  [servers]
  (first
   (keep-indexed (fn [idx server]
                   (when-not (:busy? server) idx))
                 servers)))

(defn- next-departure-index
  [servers]
  (when (some :busy? servers)
    (apply min-key #(get-in servers [% :busy-until])
           (keep-indexed (fn [idx server] (when (:busy? server) idx)) servers))))

(defn- start-service
  [rng scenario current-time patient-arrival-time servers server-idx]
  (let [server (nth servers server-idx)
        rate   (service-rate-for-role scenario (:role server))
        service-duration (rng/sample-exponential rng rate)
        service-end (+ current-time service-duration)
        service-time-inside-shift (max 0.0
                                       (- (min service-end (:shift-length-hours scenario))
                                          current-time))]
    {:servers (assoc servers server-idx
                     (-> server
                         (assoc :busy? true)
                         (assoc :busy-until service-end)
                         (update :busy-time + service-time-inside-shift)))
     :wait-time (- current-time patient-arrival-time)
     :service-end service-end}))

(defn run-one-shift
  [scenario staffing {:keys [seed record-event-log?] :or {seed 42 record-event-log? false}}]
  (when (zero? (params/total-servers staffing))
    (throw (ex-info "Staffing must include at least one server (:nurses or :pit-physicians > 0)."
                    {:staffing staffing})))
  (let [rng       (rng/make-rng seed)
        horizon   (:shift-length-hours scenario)
        arrivals  (arrivals/piecewise-hourly-arrivals rng (:hourly-arrival-rates scenario) horizon)
        servers0  (initial-servers staffing)]
    (loop [current-time 0.0
           future-arrivals arrivals
           waiting-queue []
           servers servers0
           patients-arrived 0
           patients-completed 0
           total-wait 0.0
           total-system 0.0
           queue-area 0.0
           last-event-time 0.0
           service-records []
           event-log []]
      (let [arrival-time (first future-arrivals)
            dep-idx      (next-departure-index servers)
            dep-time     (when dep-idx (get-in servers [dep-idx :busy-until]))]
        (cond
          (and (nil? arrival-time) (nil? dep-idx))
          (let [nurse-busy (reduce + (for [s servers :when (= :nurse (:role s))] (:busy-time s)))
                pit-busy   (reduce + (for [s servers :when (= :pit (:role s))] (:busy-time s)))
                nurses     (:nurses staffing)
                pits       (:pit-physicians staffing)
                available-nurse-time (* horizon nurses)
                available-pit-time   (* horizon pits)
                arrived (double patients-arrived)
                completed (double patients-completed)]
            {:avg-wait-time       (if (pos? completed) (/ total-wait completed) 0.0)
             :avg-time-in-system  (if (pos? completed) (/ total-system completed) 0.0)
             :avg-queue-length    (if (pos? horizon) (/ queue-area horizon) 0.0)
             :avg-total-idle-time (/ (+ (- available-nurse-time nurse-busy)
                                        (- available-pit-time pit-busy))
                                     (double (max 1 (params/total-servers staffing))))
             :nurse-utilization   (if (pos? nurses) (/ nurse-busy available-nurse-time) 0.0)
             :pit-utilization     (if (pos? pits) (/ pit-busy available-pit-time) 0.0)
             :patients-arrived    arrived
             :patients-completed  completed
             :service-records     service-records
             :event-log           (when record-event-log? event-log)})

          (or (and arrival-time (nil? dep-idx))
              (and arrival-time dep-time (< arrival-time dep-time)))
          (let [event-time arrival-time
                queue-area' (+ queue-area (* (count waiting-queue)
                                             (- (min event-time horizon)
                                                (min last-event-time horizon))))
                idle-idx (idle-server-index servers)]
            (if idle-idx
              (let [{servers' :servers
                     wait-time :wait-time
                     service-end :service-end}
                    (start-service rng scenario event-time event-time servers idle-idx)
                    record {:arrival-time event-time
                            :service-start-time event-time
                            :service-end-time service-end
                            :server-idx idle-idx
                            :wait-time wait-time}]
                (recur event-time
                       (next future-arrivals)
                       waiting-queue
                       servers'
                       (inc patients-arrived)
                       patients-completed
                       total-wait
                       total-system
                       queue-area'
                       event-time
                       (conj service-records record)
                       (cond-> event-log
                         record-event-log? (conj {:time event-time :event :arrival-and-start :server idle-idx}))))
              (recur event-time
                     (next future-arrivals)
                     (conj waiting-queue event-time)
                     servers
                     (inc patients-arrived)
                     patients-completed
                     total-wait
                     total-system
                     queue-area'
                     event-time
                     service-records
                     (cond-> event-log
                       record-event-log? (conj {:time event-time :event :arrival-queued})))))

          :else
          (let [event-time dep-time
                queue-area' (+ queue-area (* (count waiting-queue)
                                             (- (min event-time horizon)
                                                (min last-event-time horizon))))
                completed-rec-idx (first
                                   (keep-indexed
                                    (fn [idx rec]
                                      (when (and (nil? (:closed? rec))
                                                 (= dep-idx (:server-idx rec))
                                                 (= dep-time (:service-end-time rec)))
                                        idx))
                                    service-records))
                rec (when completed-rec-idx (nth service-records completed-rec-idx))
                _   (when (nil? completed-rec-idx)
                      (throw (ex-info "Internal: departure with no matching open service record"
                                      {:dep-idx dep-idx
                                       :dep-time dep-time
                                       :n-open (count (remove :closed? service-records))})))
                total-system' (+ total-system (- dep-time (:arrival-time rec)))]
            (if (seq waiting-queue)
              (let [next-arrival (first waiting-queue)
                    freed-server  (assoc (nth servers dep-idx) :busy? false :busy-until Double/POSITIVE_INFINITY)
                    servers-reset  (assoc servers dep-idx freed-server)
                    {servers' :servers
                     wait-time :wait-time
                     service-end :service-end}
                    (start-service rng scenario event-time next-arrival servers-reset dep-idx)
                    updated-records (cond-> service-records
                                      completed-rec-idx (assoc completed-rec-idx (assoc rec :closed? true))
                                      true (conj {:arrival-time next-arrival
                                                  :service-start-time event-time
                                                  :service-end-time service-end
                                                  :server-idx dep-idx
                                                  :wait-time wait-time}))]
                (recur event-time
                       future-arrivals
                       (vec (rest waiting-queue))
                       servers'
                       patients-arrived
                       (inc patients-completed)
                       (+ total-wait wait-time)
                       total-system'
                       queue-area'
                       event-time
                       updated-records
                       (cond-> event-log
                         record-event-log? (conj {:time event-time :event :departure-and-start :server dep-idx}))))
              (let [freed-server (assoc (nth servers dep-idx) :busy? false :busy-until Double/POSITIVE_INFINITY)
                    servers' (assoc servers dep-idx freed-server)
                    updated-records (cond-> service-records
                                      completed-rec-idx (assoc completed-rec-idx (assoc rec :closed? true)))]
                (recur event-time
                       future-arrivals
                       waiting-queue
                       servers'
                       patients-arrived
                       (inc patients-completed)
                       total-wait
                       total-system'
                       queue-area'
                       event-time
                       updated-records
                       (cond-> event-log
                         record-event-log? (conj {:time event-time :event :departure :server dep-idx})))))))))))

(defn run-shift
  [scenario staffing {:keys [replications seed record-event-log?]
                      :or   {replications 20 seed 42 record-event-log? false}}]
  (let [summaries
        (vec
         (for [rep (range replications)]
           (run-one-shift scenario staffing
                          {:seed (+ seed rep)
                           :record-event-log? record-event-log?})))]
    (merge
     {:scenario-id (:scenario-id scenario)
      :staffing staffing
      :replications replications
      :staffing-cost (params/staffing-cost scenario staffing)
      :within-budget? (params/within-budget? scenario staffing)}
     (metrics/summarize-replications summaries))))
