(ns bodyshop.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [bodyshop.robotics :as robotics]
            [bodyshop.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Meridian Sedan Body Shell BS-3301" (:shell-name (store/body-shell s "shell-1"))))
      (is (= "JPN" (:jurisdiction (store/body-shell s "shell-1"))))
      (is (= :DP600 (:rail-material-grade (store/body-shell s "shell-1"))))
      (is (= 4665 (:overall-length-actual-mm (store/body-shell s "shell-1"))))
      (is (= 4650 (:overall-length-min-mm (store/body-shell s "shell-1"))))
      (is (= 4680 (:overall-length-max-mm (store/body-shell s "shell-1"))))
      (is (false? (:weld-quality-defect-unresolved? (store/body-shell s "shell-1"))))
      (is (= 4720 (:overall-length-actual-mm (store/body-shell s "shell-3"))))
      (is (true? (:weld-quality-defect-unresolved? (store/body-shell s "shell-4"))))
      (is (false? (:robotics-sim-verified? (store/body-shell s "shell-1"))) "no robotics mission has run yet")
      (is (true? (:robotics-sim-verified? (store/body-shell s "shell-5"))) "seeded as already-on-file")
      (is (= 250000 (:press-die-mass-kg (store/body-shell s "shell-5"))))
      (is (> (:sim-peak-forming-pressure-mpa (store/body-shell s "shell-5"))
             (robotics/forming-pressure-ceiling-mpa :DP600))
          "shell-5's real physics-2d-simulated forming pressure exceeds the DP600 ceiling")
      (is (< (:sim-peak-forming-pressure-mpa (store/body-shell s "shell-1"))
             (robotics/forming-pressure-ceiling-mpa :DP600))
          "shell-1's real physics-2d-simulated forming pressure clears the DP600 ceiling")
      (is (= 468.75 (:sim-peak-forming-pressure-mpa (store/body-shell s "shell-1"))))
      (is (= 1953.125 (:sim-peak-forming-pressure-mpa (store/body-shell s "shell-5"))))
      (is (false? (:body-shell-shipped? (store/body-shell s "shell-1"))))
      (is (false? (:body-certified? (store/body-shell s "shell-1"))))
      (is (= ["shell-1" "shell-2" "shell-3" "shell-4" "shell-5"]
             (mapv :id (store/all-body-shells s))))
      (is (nil? (store/weld-quality-screen-of s "shell-1")))
      (is (nil? (store/material-cert-verification-of s "shell-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/shipment-history s)))
      (is (= [] (store/certificate-history s)))
      (is (zero? (store/next-shipment-sequence s "JPN")))
      (is (zero? (store/next-certificate-sequence s "JPN")))
      (is (false? (store/body-shell-already-shipped? s "shell-1")))
      (is (false? (store/body-shell-already-certified? s "shell-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :body-shell/upsert
                                 :value {:id "shell-1" :shell-name "Meridian Sedan Body Shell BS-3301"}})
        (is (= "Meridian Sedan Body Shell BS-3301" (:shell-name (store/body-shell s "shell-1"))))
        (is (= :DP600 (:rail-material-grade (store/body-shell s "shell-1"))) "unrelated field preserved"))
      (testing "robotics-sim result commits via :body-shell/upsert and reads back"
        (store/commit-record! s {:effect :body-shell/upsert
                                 :value {:id "shell-1" :robotics-sim-verified? true
                                        :robotics-sim-record {:mission-id "m-1" :passed? true}}})
        (is (true? (:robotics-sim-verified? (store/body-shell s "shell-1"))))
        (is (= {:mission-id "m-1" :passed? true} (:robotics-sim-record (store/body-shell s "shell-1"))))
        (is (= :DP600 (:rail-material-grade (store/body-shell s "shell-1"))) "unrelated field still preserved"))
      (testing "verification / weld-quality-screen payloads commit and read back"
        (store/commit-record! s {:effect :material-cert-verification/set :path ["shell-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/material-cert-verification-of s "shell-1")))
        (store/commit-record! s {:effect :weld-quality-screen/set :path ["shell-1"]
                                 :payload {:body-shell-id "shell-1" :verdict :resolved}})
        (is (= {:body-shell-id "shell-1" :verdict :resolved} (store/weld-quality-screen-of s "shell-1"))))
      (testing "body-shell shipment drafts a record and advances the sequence"
        (store/commit-record! s {:effect :body-shell/mark-shipped :path ["shell-1"]})
        (is (= "JPN-BSH-000000" (get (first (store/shipment-history s)) "record_id")))
        (is (= "body-shell-shipment-draft" (get (first (store/shipment-history s)) "kind")))
        (is (true? (:body-shell-shipped? (store/body-shell s "shell-1"))))
        (is (= 1 (count (store/shipment-history s))))
        (is (= 1 (store/next-shipment-sequence s "JPN")))
        (is (true? (store/body-shell-already-shipped? s "shell-1")))
        (is (false? (store/body-shell-already-shipped? s "shell-2"))))
      (testing "Body-in-White Quality Certificate drafts a record and advances the sequence"
        (store/commit-record! s {:effect :body-shell/mark-certified :path ["shell-1"]})
        (is (= "JPN-BIWQC-000000" (get (first (store/certificate-history s)) "record_id")))
        (is (= "body-certificate-draft" (get (first (store/certificate-history s)) "kind")))
        (is (true? (:body-certified? (store/body-shell s "shell-1"))))
        (is (= 1 (count (store/certificate-history s))))
        (is (= 1 (store/next-certificate-sequence s "JPN")))
        (is (true? (store/body-shell-already-certified? s "shell-1")))
        (is (false? (store/body-shell-already-certified? s "shell-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/body-shell s "nope")))
    (is (= [] (store/all-body-shells s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/shipment-history s)))
    (is (= [] (store/certificate-history s)))
    (is (zero? (store/next-shipment-sequence s "JPN")))
    (is (zero? (store/next-certificate-sequence s "JPN")))
    (store/with-body-shells s {"x" {:id "x" :shell-name "n" :rail-material-grade :DP600
                                     :overall-length-actual-mm 4665
                                     :overall-length-min-mm 4650 :overall-length-max-mm 4680
                                     :weld-quality-defect-unresolved? false
                                     :body-shell-shipped? false :body-certified? false
                                     :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:shell-name (store/body-shell s "x"))))))
