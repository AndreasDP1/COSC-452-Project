(ns er-staffing.evaluation.lexicase)

(defn summary->case-errors
  "Turn one V2 week summary into a vector of per-case errors (lower is better)."
  [summary]
  (let [match-rate (double (or (:specialty-matching-rate summary) 0.0))]
    [(:avg-wait-time summary)
     (:avg-time-in-system summary)
     (:avg-queue-length summary)
     (- 1.0 match-rate)
     (if (:within-budget? summary) 0.0 1.0)]))

(defn scenario-summaries->case-errors
  "Flatten multiple scenario summaries into one long case vector."
  [summaries]
  (vec (mapcat summary->case-errors summaries)))

(defn weighted-rank
  [scored]
  (sort-by :score scored))

(defn casewise-rank
  "Helper for analysis, not full lexicase selection.
   Ranks by count of case-wise wins, then by average case error."
  [candidate->cases]
  (let [n-cases (count (val (first candidate->cases)))
        mins (for [i (range n-cases)]
               (apply min (map #(nth (val %) i) candidate->cases)))]
    (sort-by (juxt (comp - :wins) :mean-error)
             (for [[candidate cases] candidate->cases]
               {:candidate candidate
                :wins (count (filter true? (map = cases mins)))
                :mean-error (/ (reduce + cases) (double (count cases)))}))))

(comment
  "================================================================================
  FILE: evaluation/lexicase.clj
  NAMESPACE: er-staffing.evaluation.lexicase

  PURPOSE
    **Offline / analysis helpers** — decompose summaries into case vectors, optional
    casewise ranking. **NOT** used by the live GA; the GA uses `evolution.selection`
    for lexicase parent selection.

  USED BY
    test/er_staffing/lexicase_test.clj

  SEE ALSO
    evolution/selection.clj — lexicase-pick, lexicase-select-n-without-replacement
  ================================================================================")
