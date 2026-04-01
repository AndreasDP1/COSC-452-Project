(ns er-staffing.random)

(defn make-rng
  [seed]
  (java.util.Random. (long seed)))

(defn rand-double
  [^java.util.Random rng]
  (.nextDouble rng))

(defn rand-int-between
  [^java.util.Random rng lo hi]
  (+ lo (.nextInt rng (inc (- hi lo)))))

(defn sample-exponential
  "Mean inter-event time = 1 / rate."
  [^java.util.Random rng rate]
  (if (<= rate 0.0)
    Double/POSITIVE_INFINITY
    (/ (- (Math/log (- 1.0 (rand-double rng)))) rate)))

(defn sample-poisson
  "Knuth sampler. Fine for moderate hourly counts."
  [^java.util.Random rng lambda]
  (cond
    (<= lambda 0.0) 0
    :else
    (let [l (Math/exp (- lambda))]
      (loop [k 0
             p 1.0]
        (if (<= p l)
          (dec k)
          (recur (inc k) (* p (rand-double rng))))))))

(defn shuffle-with-rng
  [^java.util.Random rng xs]
  (let [a (object-array xs)]
    (loop [i (dec (alength a))]
      (if (<= i 0)
        (vec a)
        (let [j (.nextInt rng (inc i))
              tmp (aget a i)]
          (aset a i (aget a j))
          (aset a j tmp)
          (recur (dec i)))))))
