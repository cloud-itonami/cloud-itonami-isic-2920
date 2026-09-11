(ns bodyshop.governor-contract-test
  "The governor contract as executable tests -- the body-shop-plant
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    Body Shop Advisor never ships a body-shell action or issues a
    Body-in-White Quality Certificate the Stamping Governor would
    reject, `:actuation/ship-body-shell`/`:actuation/issue-body-
    certificate` NEVER auto-commit at any phase, `:body-shell/intake`
    (no direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [bodyshop.store :as store]
            [bodyshop.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a material-cert
  evidence verification on file. Uses distinct thread-ids per call
  site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :material-cert-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through end-of-line weld-quality screening ->
  approve, leaving a screening on file. Only safe to call for a
  body-shell whose defect status has already resolved -- an unresolved
  defect HARD-holds the screen itself (see
  `weld-quality-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :end-of-line-quality/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(defn- simulate-robotics!
  "Walks `subject` through the robot stamping-press-forming
  verification mission -> approve, leaving `:robotics-sim-verified?`
  on file. Only meaningful to call for a body-shell whose REAL
  `physics-2d`-simulated forming-pressure telemetry
  (`:sim-peak-forming-pressure-mpa`) is actually within tolerance -- an
  out-of-tolerance body-shell still gets :robotics-sim-verified?
  recorded (per whatever the mission itself found), but `bodyshop.
  governor`'s independent recheck HARD-holds regardless (see
  `robotics-simulation-out-of-tolerance-is-held`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-stamping-press :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :body-shell/intake :subject "shell-1"
                   :patch {:id "shell-1" :shell-name "Meridian Sedan Body Shell BS-3301"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Meridian Sedan Body Shell BS-3301" (:shell-name (store/body-shell db "shell-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :material-cert-rules/verify :subject "shell-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/material-cert-verification-of db "shell-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a material-cert-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :material-cert-rules/verify :subject "shell-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/material-cert-verification-of db "shell-2")) "no verification written"))))

(deftest ship-body-shell-without-verification-is-held
  (testing "actuation/ship-body-shell before any material-cert evidence verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/ship-body-shell :subject "shell-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest body-shell-dimension-out-of-range-is-held
  (testing "a body-shell whose own overall-length falls outside its own nominal-dimension bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "shell-3")
          _ (simulate-robotics! actor "t5pre2" "shell-3")
          res (exec-op actor "t5" {:op :actuation/ship-body-shell :subject "shell-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:body-shell-dimension-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest weld-quality-defect-is-held-and-unoverridable
  (testing "an unresolved weld-quality defect on a body-shell -> HOLD, and never reaches request-approval -- exercised via :end-of-line-quality/screen DIRECTLY, not via the actuation op against an unscreened body-shell (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / every prior sibling's ADR-0001)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :end-of-line-quality/screen :subject "shell-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:weld-quality-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/weld-quality-screen-of db "shell-4")) "no clearance written"))))

(deftest ship-body-shell-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-spec body-shell still ALWAYS interrupts for human approval -- actuation/ship-body-shell is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "shell-1")
          _ (simulate-robotics! actor "t7pre2" "shell-1")
          r1 (exec-op actor "t7" {:op :actuation/ship-body-shell :subject "shell-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, shipment record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:body-shell-shipped? (store/body-shell db "shell-1"))))
          (is (= 1 (count (store/shipment-history db))) "one draft shipment record"))))))

(deftest issue-body-certificate-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-defect body-shell still ALWAYS interrupts for human approval -- actuation/issue-body-certificate is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "shell-1")
          _ (screen! actor "t8pre2" "shell-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-body-certificate :subject "shell-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certificate record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:body-certified? (store/body-shell db "shell-1"))))
          (is (= 1 (count (store/certificate-history db))) "one draft certificate record"))))))

(deftest ship-body-shell-double-shipment-is-held
  (testing "shipping the same body-shell's action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "shell-1")
          _ (simulate-robotics! actor "t9pre2" "shell-1")
          _ (exec-op actor "t9a" {:op :actuation/ship-body-shell :subject "shell-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/ship-body-shell :subject "shell-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-shipped} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/shipment-history db))) "still only the one earlier shipment"))))

(deftest issue-body-certificate-double-issuance-is-held
  (testing "issuing the same body-shell's Body-in-White Quality Certificate twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "shell-1")
          _ (screen! actor "t10pre2" "shell-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-body-certificate :subject "shell-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-body-certificate :subject "shell-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certificate-history db))) "still only the one earlier certificate issuance"))))

(deftest robotics-simulation-always-needs-approval
  (testing "robotics/simulate-stamping-press is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t11" {:op :robotics/simulate-stamping-press :subject "shell-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t11")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:robotics-sim-verified? (store/body-shell db "shell-1"))))))))

(deftest ship-body-shell-without-robotics-simulation-is-held
  (testing "actuation/ship-body-shell before the robot stamping-press-forming mission ever ran -> HOLD (robotics-simulation-missing)"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "shell-1")
          res (exec-op actor "t12" {:op :actuation/ship-body-shell :subject "shell-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-missing} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest robotics-simulation-out-of-tolerance-is-held
  (testing "shell-5 has a robotics-sim already on file, but its own REAL physics-2d-simulated forming-pressure reading exceeds its own rail-material grade's ceiling on INDEPENDENT recheck -> HOLD, never trusts the on-file verdict alone"
    (let [[db actor] (fresh)
          _ (verify! actor "t13pre" "shell-5")
          res (exec-op actor "t13" {:op :actuation/ship-body-shell :subject "shell-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:robotics-simulation-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/shipment-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :body-shell/intake :subject "shell-1"
                          :patch {:id "shell-1" :shell-name "Meridian Sedan Body Shell BS-3301"}} operator)
      (exec-op actor "b" {:op :material-cert-rules/verify :subject "shell-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
