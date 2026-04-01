(ns er-staffing.evaluation.lexicase)

(defn summary->case-errors
  "Turn one summary into a vector of case-wise errors.
   Lower is better in every case."
  [summary]
  [(:avg-wait-time summary)
   (:avg-time-in-system summary)
   (:avg-total-idle-time summary)
   (if (:within-budget? summary) 0.0 1.0)])

(defn scenario-summaries->case-errors
  "Flatten multiple scenario summaries into one long case vector.
   Useful later if the team wants lexicase over multiple demand conditions."
  [summaries]
  (vec (mapcat summary->case-errors summaries)))

(defn weighted-rank
  [scored]
  (sort-by :score scored))

(defn casewise-rank
  "A simple helper for analysis, not full lexicase selection.
   Ranks by count of case-wise wins, then by average case error."
  [candidate->cases]
  (let [n-cases (count (val (first candidate->cases)))
        mins    (for [i (range n-cases)]
                  (apply min (map #(nth (val %) i) candidate->cases)))]
    (sort-by (juxt (comp - :wins) :mean-error)
             (for [[candidate cases] candidate->cases]
               {:candidate candidate
                :wins (count (filter true? (map = cases mins)))
                :mean-error (/ (reduce + cases) (double (count cases)))}))))
