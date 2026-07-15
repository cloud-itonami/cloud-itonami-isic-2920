(ns bodyshop.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean body-shell
  through intake -> (an evidence-incomplete shipment attempt) ->
  material-cert-rules verification -> end-of-line weld-quality
  screening -> robot stamping-press-forming mission -> body-shell-
  shipment proposal (always escalates) -> human approval -> commit,
  then through Body-in-White Quality Certificate proposal (always
  escalates) -> human approval -> commit, then shows every HARD hold
  this actor defends against (a jurisdiction with no spec-basis, an
  actuation attempted before any material-cert evidence verification,
  an actuation attempted before the robot stamping-press mission ever
  ran, an out-of-spec body-shell dimension, a robotics mission on file
  whose independent recheck disagrees, an unresolved weld-quality
  defect screened directly via `:end-of-line-quality/screen` [never
  via an actuation op against an unscreened body-shell -- see this
  actor's own governor ns docstring / the lesson parksafety's
  ADR-2607071922 Decision 5, and every prior sibling's ADR-0001
  already recorded, most recently `autoparts`'s], and a double
  body-shell-shipment/certificate-issuance of an already-processed
  body-shell) that never reach a human at all, and prints the audit
  ledger + the draft body-shell-shipment and body-certificate
  records."
  (:require [langgraph.graph :as g]
            [bodyshop.export :as export]
            [bodyshop.store :as store]
            [bodyshop.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== body-shell/intake shell-1 (JPN, clean; dimension within spec, no weld-quality defect) ==")
    (println (exec! actor "t1" {:op :body-shell/intake :subject "shell-1"
                                :patch {:id "shell-1" :shell-name "Meridian Sedan Body Shell BS-3301"}} operator))

    (println "== actuation/ship-body-shell shell-1 before any material-cert evidence verification -> HARD hold (evidence-incomplete) ==")
    (println (exec! actor "t1b" {:op :actuation/ship-body-shell :subject "shell-1"} operator))

    (println "== material-cert-rules/verify shell-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :material-cert-rules/verify :subject "shell-1"} operator))
    (println (approve! actor "t2"))

    (println "== end-of-line-quality/screen shell-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :end-of-line-quality/screen :subject "shell-1"} operator))
    (println (approve! actor "t3"))

    (println "== robotics/simulate-stamping-press shell-1 (real physics-2d press-die/blank forming mission; escalates -- human approves) ==")
    (println (exec! actor "t3b" {:op :robotics/simulate-stamping-press :subject "shell-1"} operator))
    (println (approve! actor "t3b"))

    (println "== actuation/ship-body-shell shell-1 (always escalates -- actuation/ship-body-shell) ==")
    (let [r (exec! actor "t4" {:op :actuation/ship-body-shell :subject "shell-1"} operator)]
      (println r)
      (println "-- human quality engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-body-certificate shell-1 (always escalates -- actuation/issue-body-certificate) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-body-certificate :subject "shell-1"} operator)]
      (println r)
      (println "-- human quality engineer approves --")
      (println (approve! actor "t5")))

    (println "== material-cert-rules/verify shell-2 (ATL, no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :material-cert-rules/verify :subject "shell-2"} operator))

    (println "== material-cert-rules/verify shell-3 (escalates -- human approves; sets up the out-of-spec dimension test) ==")
    (println (exec! actor "t7" {:op :material-cert-rules/verify :subject "shell-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/ship-body-shell shell-3 before robotics -> HARD hold (robotics-simulation-missing) ==")
    (println (exec! actor "t7b" {:op :actuation/ship-body-shell :subject "shell-3"} operator))

    (println "== robotics/simulate-stamping-press shell-3 (real physics-2d simulation clears the forming-pressure ceiling; escalates -- human approves) ==")
    (println (exec! actor "t7c" {:op :robotics/simulate-stamping-press :subject "shell-3"} operator))
    (println (approve! actor "t7c"))

    (println "== actuation/ship-body-shell shell-3 (overall-length 4720mm outside [4650,4680]mm nominal-dimension bounds -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/ship-body-shell :subject "shell-3"} operator))

    (println "== actuation/ship-body-shell shell-5 (robotics-sim on file, but real physics-2d-simulated forming pressure exceeds the DP600 ceiling on independent recheck -> HARD hold) ==")
    (println (exec! actor "t8b" {:op :material-cert-rules/verify :subject "shell-5"} operator))
    (println (approve! actor "t8b"))
    (println (exec! actor "t8c" {:op :actuation/ship-body-shell :subject "shell-5"} operator))

    (println "== end-of-line-quality/screen shell-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :end-of-line-quality/screen :subject "shell-4"} operator))

    (println "== actuation/ship-body-shell shell-1 AGAIN (double-shipment -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/ship-body-shell :subject "shell-1"} operator))

    (println "== actuation/issue-body-certificate shell-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-body-certificate :subject "shell-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft body-shell-shipment records ==")
    (doseq [r (store/shipment-history db)] (println r))

    (println "== draft body-certificate records ==")
    (doseq [r (store/certificate-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
