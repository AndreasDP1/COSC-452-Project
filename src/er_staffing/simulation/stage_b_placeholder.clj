(ns er-staffing.simulation.stage-b-placeholder)

(defn planned-stage-b-notes
  []
  {:status :not-implemented
   :idea "All patients first see a triage nurse; some fraction are then routed to PIT review."
   :next-steps ["Add patient classes / routing probabilities"
                "Track two queues or two service stages"
                "Measure stage-specific waiting times"
                "Reuse Stage A replication and metrics wrappers where possible"]})
