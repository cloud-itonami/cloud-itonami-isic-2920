(ns bodyshop.export-test
  "Audit-package export contract -- social/regulatory hand-off shape."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [bodyshop.export :as export]
            [bodyshop.operation :as op]
            [bodyshop.store :as store]))

(def operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- seed-with-one-shipment []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :material-cert-rules/verify :subject "shell-1"})
    (approve! actor "v")
    (exec! actor "r" {:op :robotics/simulate-stamping-press :subject "shell-1"})
    (approve! actor "r")
    (exec! actor "d" {:op :actuation/ship-body-shell :subject "shell-1"})
    (approve! actor "d")
    db))

(deftest audit-package-shape
  (let [db (seed-with-one-shipment)
        pkg (export/audit-package db)]
    (is (= "2920" (:isic pkg)))
    (is (= "cloud-itonami-isic-2920" (:business-id pkg)))
    (is (= :edn-maps (:format pkg)))
    (is (pos? (get-in pkg [:counts :ledger])))
    (is (= 1 (get-in pkg [:counts :shipments])))
    (is (some #(= "shell-1" (:id %)) (:body-shells pkg)))
    (is (true? (:body-shell-shipped?
                (first (filter #(= "shell-1" (:id %)) (:body-shells pkg))))))))

(deftest csv-bundle-has-headers-and-rows
  (let [db (seed-with-one-shipment)
        bundle (export/package->csv-bundle db)]
    (is (every? bundle ["body-shells.csv" "ledger.csv" "shipments.csv" "body-certificates.csv"]))
    (is (str/starts-with? (get bundle "body-shells.csv") "id,shell-name,"))
    (is (re-find #"shell-1" (get bundle "body-shells.csv")))
    (is (re-find #"JPN-BSH-000000" (get bundle "shipments.csv")))
    (is (re-find #":actuation/ship-body-shell" (get bundle "ledger.csv")))))

(deftest empty-store-export-is-usable
  (let [db (store/seed-db)
        pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (is (= 0 (get-in pkg [:counts :shipments])))
    (is (= 5 (get-in pkg [:counts :body-shells])))
    (is (str/includes? (get bundle "ledger.csv") "seq,t,op"))))
