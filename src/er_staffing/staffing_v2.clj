(ns er-staffing.staffing-v2
  "Flat 7-gene staffing genome: RT, Cardio, Radio, MH, Physician, PA, Nurse.")

(def role-order
  [:rt :cardio :radio :mh :physician :pa :nurse])

(defn staffing-map?
  [m]
  (and (map? m)
       (every? (fn [k] (contains? m k)) role-order)))

(defn total-staff
  [staffing]
  (reduce + (map #(long (get staffing % 0)) role-order)))

(defn staffing->server-roles
  "Expand counts into one server entry per person, stable order."
  [staffing]
  (vec
   (mapcat (fn [role]
             (repeat (long (get staffing role 0)) role))
           role-order)))

(defn daily-staffing-cost
  "Cost for one 24h calendar day: sum_i count_i * hourly_cost_i."
  [scenario staffing]
  (let [prov (:providers scenario)]
    (* 24.0
       (reduce + 0.0
               (map (fn [role]
                      (* (double (get staffing role 0))
                         (double (:hourly-cost (get prov role)))))
                    role-order)))))

(defn within-daily-budget?
  [scenario staffing]
  (<= (daily-staffing-cost scenario staffing)
      (double (:daily-budget-cap scenario))))

(comment
  "================================================================================
  FILE: staffing_v2.clj
  NAMESPACE: er-staffing.staffing-v2

  PURPOSE
    Seven-gene staffing genome (RT, Cardio, Radio, MH, Physician, PA, Nurse):
    validation, expansion to server list, **24h daily payroll** vs scenario provider
    hourly rates, budget check.

  INPUTS / OUTPUTS
    Plain maps keyed by role -> counts; costs are floats for fitness and repair.
  ================================================================================")
