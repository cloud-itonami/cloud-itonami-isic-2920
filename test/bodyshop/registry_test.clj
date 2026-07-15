(ns bodyshop.registry-test
  (:require [clojure.test :refer [deftest is]]
            [bodyshop.registry :as r]))

;; ----------------------------- body-shell-dimension-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/body-shell-dimension-out-of-range? {:overall-length-actual-mm 4665 :overall-length-min-mm 4650 :overall-length-max-mm 4680})))
  (is (not (r/body-shell-dimension-out-of-range? {:overall-length-actual-mm 4650 :overall-length-min-mm 4650 :overall-length-max-mm 4680})))
  (is (not (r/body-shell-dimension-out-of-range? {:overall-length-actual-mm 4680 :overall-length-min-mm 4650 :overall-length-max-mm 4680}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/body-shell-dimension-out-of-range? {:overall-length-actual-mm 4600 :overall-length-min-mm 4650 :overall-length-max-mm 4680}))
  (is (r/body-shell-dimension-out-of-range? {:overall-length-actual-mm 4720 :overall-length-min-mm 4650 :overall-length-max-mm 4680})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/body-shell-dimension-out-of-range? {})))
  (is (not (r/body-shell-dimension-out-of-range? {:overall-length-actual-mm 4720}))))

;; ----------------------------- register-body-shell-shipment -----------------------------

(deftest shipment-is-a-draft-not-a-real-shipment
  (let [result (r/register-body-shell-shipment "shell-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest shipment-assigns-shipment-number
  (let [result (r/register-body-shell-shipment "shell-1" "JPN" 7)]
    (is (= (get result "shipment_number") "JPN-BSH-000007"))
    (is (= (get-in result ["record" "body_shell_id"]) "shell-1"))
    (is (= (get-in result ["record" "kind"]) "body-shell-shipment-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest shipment-validation-rules
  (is (thrown? Exception (r/register-body-shell-shipment "" "JPN" 0)))
  (is (thrown? Exception (r/register-body-shell-shipment "shell-1" "" 0)))
  (is (thrown? Exception (r/register-body-shell-shipment "shell-1" "JPN" -1))))

;; ----------------------------- register-body-certificate -----------------------------

(deftest certificate-is-a-draft-not-real-certification
  (let [result (r/register-body-certificate "shell-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certificate-assigns-certificate-number
  (let [result (r/register-body-certificate "shell-1" "JPN" 3)]
    (is (= (get result "certificate_number") "JPN-BIWQC-000003"))
    (is (= (get-in result ["record" "body_shell_id"]) "shell-1"))
    (is (= (get-in result ["record" "kind"]) "body-certificate-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certificate-validation-rules
  (is (thrown? Exception (r/register-body-certificate "" "JPN" 0)))
  (is (thrown? Exception (r/register-body-certificate "shell-1" "" 0)))
  (is (thrown? Exception (r/register-body-certificate "shell-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-body-shell-shipment "shell-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-body-shell-shipment "shell-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-BSH-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-BSH-000001" (get-in hist2 [1 "record_id"])))))
