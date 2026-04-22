(ns er-staffing.simulation.routing
  "Specialist-first routing with optional forced mismatch when queue or wait is high.")

(def specialist-roles [:rt :cardio :radio :mh])
(def generalist-roles [:physician :pa :nurse])

(defn specialist-kind?
  [providers role-kw]
  (= :specialist (:kind (get providers role-kw))))

(defn generalist-kind?
  [providers role-kw]
  (= :generalist (:kind (get providers role-kw))))

(defn specialist-treats?
  [providers role-kw condition]
  (let [t (:treats (get providers role-kw))]
    (and (set? t) (contains? t condition))))

(defn role-at
  [servers idx]
  (:role (nth servers idx)))

(defn idle-indices
  [servers]
  (vec (keep-indexed (fn [i s] (when-not (:busy? s) i)) servers)))

(defn effective-service-rate
  "Applies configured multipliers on top of provider base service rate (events/hour style)."
  [providers rate-mults role-kw condition]
  (let [p (get providers role-kw)
        base (double (:base-service-rate p))]
    (cond
      (generalist-kind? providers role-kw)
      (* base
         (double (case role-kw
                   :physician (:physician rate-mults)
                   :pa (:pa rate-mults)
                   (:nurse rate-mults))))
      (specialist-treats? providers role-kw condition)
      (* base (double (:specialist-match rate-mults)))
      :else
      (* base (double (:specialist-mismatch rate-mults))))))

(defn choose-server
  "Returns {:server-idx i :service-kind :specialist-match|:generalist|:specialist-mismatch} or nil.
   Order: (1) matching specialist, (2) any generalist, (3) if forced — mismatched specialist."
  [scenario servers patient-condition wait-hours queue-len]
  (let [prov (:providers scenario)
        rout (:routing scenario)
        q-th (long (or (:queue-threshold rout) 12))
        w-th (double (or (:wait-threshold-hours rout) 1.5))
        forced? (or (> (long queue-len) q-th)
                    (> (double wait-hours) w-th))
        idle (set (idle-indices servers))
        pick (fn [role pred?]
               (some (fn [idx]
                       (when (and (contains? idle idx)
                                  (= role (role-at servers idx))
                                  (pred? idx))
                         idx))
                     (range (count servers))))]
    (when (seq idle)
      (or
       ;; 1) Matching specialist (fixed role order)
       (some (fn [role]
               (when (specialist-treats? prov role patient-condition)
                 (when-let [idx (pick role (constantly true))]
                   {:server-idx idx :service-kind :specialist-match})))
             specialist-roles)
       ;; 2) Generalists: physician > PA > nurse
       (some (fn [role]
               (when (generalist-kind? prov role)
                 (when-let [idx (pick role (constantly true))]
                   {:server-idx idx :service-kind :generalist})))
             generalist-roles)
       ;; 3) Forced mismatch specialist
       (when forced?
         (some (fn [role]
                 (when (specialist-kind? prov role)
                   (when-let [idx (pick role (constantly true))]
                     {:server-idx idx :service-kind :specialist-mismatch})))
               specialist-roles))))))
