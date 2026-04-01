(ns er-staffing.lexicase-test
  (:require [clojure.test :refer [deftest is]]
            [er-staffing.evaluation.lexicase :as lex]))

(deftest casewise-rank-counts-dimension-wins
  (let [ranked (lex/casewise-rank {:a [1.0 2.0 3.0] :b [0.5 2.0 1.0]})
        by     (into {} (map (juxt :candidate :wins) ranked))]
    (is (= 3 (:wins (first ranked))))
    (is (= {:a 1 :b 3} by))))
