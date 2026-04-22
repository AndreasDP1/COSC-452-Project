(ns er-staffing.routing-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.simulation.routing :as r]))

(def prov
  {:rt {:kind :specialist :treats #{:respiratory}}
   :cardio {:kind :specialist :treats #{:cardio}}
   :radio {:kind :specialist :treats #{:trauma}}
   :mh {:kind :specialist :treats #{:psych}}
   :physician {:kind :generalist :treats :all}
   :pa {:kind :generalist :treats :all}
   :nurse {:kind :generalist :treats :all}})

(def scen
  {:providers prov
   :rate-multipliers {:specialist-match 1.5 :specialist-mismatch 0.5
                      :physician 1.0 :pa 0.75 :nurse 0.5}
   :routing {:queue-threshold 100 :wait-threshold-hours 99.0}})

(defn srv
  [role busy?]
  {:role role :busy? busy? :busy-until 0 :busy-time 0.0})

(deftest matching-specialist-preferred
  (let [servers [(srv :rt false) (srv :nurse false)]
        ch (r/choose-server scen servers :respiratory 0.0 1)]
    (is (= :rt (:role (nth servers (:server-idx ch)))))
    (is (= :specialist-match (:service-kind ch)))))

(deftest generalist-when-no-specialist
  (let [servers [(srv :cardio true) (srv :physician false)]
        ch (r/choose-server scen servers :respiratory 0.5 1)]
    (is (= :generalist (:service-kind ch)))))
