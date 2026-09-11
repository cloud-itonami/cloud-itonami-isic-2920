(ns bodyshop.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/ship-body-shell`/`:actuation/issue-body-
  certificate` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [bodyshop.phase :as phase]))

(deftest ship-body-shell-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real robot body-shell shipment"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/ship-body-shell))
          (str "phase " n " must not auto-commit :actuation/ship-body-shell")))))

(deftest issue-body-certificate-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real Body-in-White Quality Certificate"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-body-certificate))
          (str "phase " n " must not auto-commit :actuation/issue-body-certificate")))))

(deftest end-of-line-quality-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :end-of-line-quality/screen))
          (str "phase " n " must not auto-commit :end-of-line-quality/screen")))))

(deftest robotics-simulate-stamping-press-never-auto-at-any-phase
  (testing "the robot stamping-press-forming verification mission carries no direct capital risk, but is still never auto-eligible, matching every sibling verification op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :robotics/simulate-stamping-press))
          (str "phase " n " must not auto-commit :robotics/simulate-stamping-press")))))

(deftest robotics-simulate-stamping-press-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :robotics/simulate-stamping-press))
  (is (contains? (:writes (get phase/phases 3)) :robotics/simulate-stamping-press))
  (is (not (contains? (:writes (get phase/phases 1)) :robotics/simulate-stamping-press))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":body-shell/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:body-shell/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :body-shell/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/ship-body-shell} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-body-certificate} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :body-shell/intake} :commit)))))
