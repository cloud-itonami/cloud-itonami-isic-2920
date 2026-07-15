(ns bodyshop.store
  "SSoT for the body-in-white (BIW) stamping/welding body-shop actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every prior `cloud-itonami-isic-*` actor in this fleet
  uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/bodyshop/store_contract_test.clj), which is the whole point:
  the actor, the Stamping Governor and the audit ledger never know
  which SSoT they run on.

  Like `automotive.store`'s dual vehicle-dispatch/conformity-
  certificate history and `autoparts.store`'s dual part-lot-shipment/
  ppap-certificate history, this actor has TWO actuation events
  (shipping a body-shell onward to the final-assembly plant, issuing a
  Body-in-White Quality Certificate) acting on the SAME entity (a
  body-shell), each with its OWN history collection, sequence counter
  and dedicated double-actuation-guard boolean (`:body-shell-shipped?`/
  `:body-certified?`, never a `:status` value) -- the same discipline
  every prior sibling governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which body-shell was
  screened for an unresolved weld-quality defect, which body-shell
  shipment was dispatched onward to final assembly, which Body-in-
  White Quality Certificate was issued, on what jurisdictional basis,
  approved by whom' is always a query over an immutable log -- the
  audit trail a community trusting a body-shop plant needs, and the
  evidence a plant needs if a shipment or certificate decision is
  later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [bodyshop.registry :as registry]
            [bodyshop.robotics :as robotics]
            [langchain.db :as d]))

(defprotocol Store
  (body-shell [s id])
  (all-body-shells [s])
  (weld-quality-screen-of [s body-shell-id] "committed end-of-line weld-quality screening verdict for a body-shell, or nil")
  (material-cert-verification-of [s body-shell-id] "committed material-cert-rules evidence verification, or nil")
  (ledger [s])
  (shipment-history [s] "the append-only body-shell-shipment history (bodyshop.registry drafts)")
  (certificate-history [s] "the append-only body-certificate history (bodyshop.registry drafts)")
  (next-shipment-sequence [s jurisdiction] "next shipment-number sequence for a jurisdiction")
  (next-certificate-sequence [s jurisdiction] "next certificate-number sequence for a jurisdiction")
  (body-shell-already-shipped? [s body-shell-id] "has this body-shell already been shipped onward?")
  (body-shell-already-certified? [s body-shell-id] "has this body-shell's Body-in-White Quality Certificate already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-body-shells [s body-shells] "replace/seed the body-shell directory (map id->body-shell)"))

;; ----------------------------- demo data -----------------------------

(defn- with-forming-telemetry
  "Merges REAL stamping-press-forming pull telemetry onto a demo
  body-shell's base fields -- `bodyshop.robotics/press-telemetry-for`
  actually runs `simulate-press`'s `physics-2d`-stepped simulation for
  this body-shell's own `:press-die-mass-kg` (ADR-2607152000), so even
  the 'already on file' seed data (as if from an earlier real
  stamping-press-run report) is genuinely simulation-derived, never
  hand-typed doubles."
  [base]
  (merge base (select-keys (robotics/press-telemetry-for base)
                           [:sim-peak-forming-force-n :sim-peak-forming-pressure-mpa])))

(defn demo-data
  "A small, self-contained body-shell set covering both actuation
  lifecycles (shipping a body-shell onward to final assembly, issuing
  a Body-in-White Quality Certificate) so the actor + tests run
  offline. `:press-die-mass-kg` (ADR-2607152000) is a permanent
  body-shell press-run-configuration field (like `:overall-length-
  actual-mm`); `:sim-peak-forming-force-n`/`:sim-peak-forming-
  pressure-mpa` are the REAL `bodyshop.robotics/simulate-press`-computed
  telemetry for that field (`with-forming-telemetry`), the ground
  truth `bodyshop.robotics/simulation-out-of-tolerance?` independently
  rechecks. shell-5 (a hatchback body-shell) is DELIBERATELY recorded
  with a much heavier `:press-die-mass-kg` (250,000 kg) than its own
  DP600 rail-material grade's forming-pressure ceiling can clear -- a
  genuine press-run-configuration inconsistency (someone/something ran
  this shell's forming cycle on an oversized press slide/die
  assembly, or logged the wrong die-set mass) that the real, re-run
  simulation catches on independent recheck even though
  `:robotics-sim-verified?` was seeded `true` (\"already on file\",
  i.e. someone/something marked it passed without this real check ever
  having run) -- the body-shop analog of automotive's misclassified
  vehicle-5 / autoparts' lot-5. shell-1/2/3/4's `:press-die-mass-kg`
  (60,000 kg each) is a genuinely consistent large-structural-panel
  transfer-press slide+die-set mass, which clears every rail-material
  grade's real forming-pressure ceiling with margin (see
  `bodyshop.robotics/forming-pressure-ceiling-mpa`)."
  []
  {:body-shells
   (into {}
         (map (fn [v] [(:id v) (with-forming-telemetry v)]))
         [{:id "shell-1" :shell-name "Meridian Sedan Body Shell BS-3301"
           :rail-material-grade :DP600
           :press-die-mass-kg 60000
           :overall-length-actual-mm 4665 :overall-length-min-mm 4650 :overall-length-max-mm 4680
           :weld-quality-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :body-shell-shipped? false :body-certified? false
           :jurisdiction "JPN" :status :intake}
          {:id "shell-2" :shell-name "Atlas Crossover Body Shell BS-1180"
           :rail-material-grade :DP600
           :press-die-mass-kg 60000
           :overall-length-actual-mm 4665 :overall-length-min-mm 4650 :overall-length-max-mm 4680
           :weld-quality-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :body-shell-shipped? false :body-certified? false
           :jurisdiction "ATL" :status :intake}
          {:id "shell-3" :shell-name "田中セダンボディシェル BS-2215"
           :rail-material-grade :DP980
           :press-die-mass-kg 60000
           :overall-length-actual-mm 4720 :overall-length-min-mm 4650 :overall-length-max-mm 4680
           :weld-quality-defect-unresolved? false
           :robotics-sim-verified? false :robotics-sim-record nil
           :body-shell-shipped? false :body-certified? false
           :jurisdiction "JPN" :status :intake}
          {:id "shell-4" :shell-name "佐藤SUVボディシェル BS-3330"
           :rail-material-grade :boron-PHS
           :press-die-mass-kg 60000
           :overall-length-actual-mm 4665 :overall-length-min-mm 4650 :overall-length-max-mm 4680
           :weld-quality-defect-unresolved? true
           :robotics-sim-verified? false :robotics-sim-record nil
           :body-shell-shipped? false :body-certified? false
           :jurisdiction "JPN" :status :intake}
          {:id "shell-5" :shell-name "鈴木ハッチバックボディシェル BS-1118"
           :rail-material-grade :DP600
           :press-die-mass-kg 250000
           :overall-length-actual-mm 4665 :overall-length-min-mm 4650 :overall-length-max-mm 4680
           :weld-quality-defect-unresolved? false
           :robotics-sim-verified? true :robotics-sim-record nil
           :body-shell-shipped? false :body-certified? false
           :jurisdiction "JPN" :status :intake}])})

;; ----------------------------- shared commit logic -----------------------------

(defn- ship-body-shell!
  "Backend-agnostic `:body-shell/mark-shipped` -- looks up the
  body-shell via the protocol and drafts the body-shell-shipment
  record, and returns {:result .. :body-shell-patch ..} for the caller
  to persist."
  [s body-shell-id]
  (let [a (body-shell s body-shell-id)
        seq-n (next-shipment-sequence s (:jurisdiction a))
        result (registry/register-body-shell-shipment body-shell-id (:jurisdiction a) seq-n)]
    {:result result
     :body-shell-patch {:body-shell-shipped? true
                        :shipment-number (get result "shipment_number")}}))

(defn- issue-body-certificate!
  "Backend-agnostic `:body-shell/mark-certified` -- looks up the
  body-shell via the protocol and drafts the Body-in-White Quality
  Certificate record, and returns {:result .. :body-shell-patch ..}
  for the caller to persist."
  [s body-shell-id]
  (let [a (body-shell s body-shell-id)
        seq-n (next-certificate-sequence s (:jurisdiction a))
        result (registry/register-body-certificate body-shell-id (:jurisdiction a) seq-n)]
    {:result result
     :body-shell-patch {:body-certified? true
                        :certificate-number (get result "certificate_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (body-shell [_ id] (get-in @a [:body-shells id]))
  (all-body-shells [_] (sort-by :id (vals (:body-shells @a))))
  (weld-quality-screen-of [_ id] (get-in @a [:weld-quality-screens id]))
  (material-cert-verification-of [_ body-shell-id] (get-in @a [:verifications body-shell-id]))
  (ledger [_] (:ledger @a))
  (shipment-history [_] (:shipments @a))
  (certificate-history [_] (:certificates @a))
  (next-shipment-sequence [_ jurisdiction] (get-in @a [:shipment-sequences jurisdiction] 0))
  (next-certificate-sequence [_ jurisdiction] (get-in @a [:certificate-sequences jurisdiction] 0))
  (body-shell-already-shipped? [_ body-shell-id] (boolean (get-in @a [:body-shells body-shell-id :body-shell-shipped?])))
  (body-shell-already-certified? [_ body-shell-id] (boolean (get-in @a [:body-shells body-shell-id :body-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :body-shell/upsert
      (swap! a update-in [:body-shells (:id value)] merge value)

      :material-cert-verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :weld-quality-screen/set
      (swap! a assoc-in [:weld-quality-screens (first path)] payload)

      :body-shell/mark-shipped
      (let [body-shell-id (first path)
            {:keys [result body-shell-patch]} (ship-body-shell! s body-shell-id)
            jurisdiction (:jurisdiction (body-shell s body-shell-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:shipment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:body-shells body-shell-id] merge body-shell-patch)
                       (update :shipments registry/append result))))
        result)

      :body-shell/mark-certified
      (let [body-shell-id (first path)
            {:keys [result body-shell-patch]} (issue-body-certificate! s body-shell-id)
            jurisdiction (:jurisdiction (body-shell s body-shell-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:certificate-sequences jurisdiction] (fnil inc 0))
                       (update-in [:body-shells body-shell-id] merge body-shell-patch)
                       (update :certificates registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-body-shells [s body-shells] (when (seq body-shells) (swap! a assoc :body-shells body-shells)) s))

(defn seed-db
  "A MemStore seeded with the demo body-shell set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :weld-quality-screens {} :ledger []
                           :shipment-sequences {} :shipments []
                           :certificate-sequences {} :certificates []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/weld-quality-screen payloads,
  ledger facts, shipment/certificate records) are stored as EDN
  strings so `langchain.db` doesn't expand them into sub-entities --
  the same convention every sibling actor's store uses."
  {:body-shell/id                     {:db/unique :db.unique/identity}
   :verification/body-shell-id        {:db/unique :db.unique/identity}
   :weld-quality-screen/body-shell-id {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :shipment/seq                      {:db/unique :db.unique/identity}
   :certificate/seq                   {:db/unique :db.unique/identity}
   :shipment-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :certificate-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- body-shell->tx [{:keys [id shell-name rail-material-grade
                               press-die-mass-kg sim-peak-forming-force-n sim-peak-forming-pressure-mpa
                               overall-length-actual-mm overall-length-min-mm overall-length-max-mm
                               weld-quality-defect-unresolved? robotics-sim-verified? robotics-sim-record
                               body-shell-shipped? body-certified?
                               jurisdiction status shipment-number certificate-number]}]
  (cond-> {:body-shell/id id}
    shell-name                                   (assoc :body-shell/shell-name shell-name)
    rail-material-grade                          (assoc :body-shell/rail-material-grade rail-material-grade)
    press-die-mass-kg                            (assoc :body-shell/press-die-mass-kg press-die-mass-kg)
    sim-peak-forming-force-n                     (assoc :body-shell/sim-peak-forming-force-n sim-peak-forming-force-n)
    (some? sim-peak-forming-pressure-mpa)        (assoc :body-shell/sim-peak-forming-pressure-mpa sim-peak-forming-pressure-mpa)
    overall-length-actual-mm                     (assoc :body-shell/overall-length-actual-mm overall-length-actual-mm)
    overall-length-min-mm                        (assoc :body-shell/overall-length-min-mm overall-length-min-mm)
    overall-length-max-mm                        (assoc :body-shell/overall-length-max-mm overall-length-max-mm)
    (some? weld-quality-defect-unresolved?)      (assoc :body-shell/weld-quality-defect-unresolved? weld-quality-defect-unresolved?)
    (some? robotics-sim-verified?)                (assoc :body-shell/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)                  (assoc :body-shell/robotics-sim-record (enc robotics-sim-record))
    (some? body-shell-shipped?)                  (assoc :body-shell/body-shell-shipped? body-shell-shipped?)
    (some? body-certified?)                      (assoc :body-shell/body-certified? body-certified?)
    jurisdiction                                 (assoc :body-shell/jurisdiction jurisdiction)
    status                                       (assoc :body-shell/status status)
    shipment-number                              (assoc :body-shell/shipment-number shipment-number)
    certificate-number                           (assoc :body-shell/certificate-number certificate-number)))

(def ^:private body-shell-pull
  [:body-shell/id :body-shell/shell-name :body-shell/rail-material-grade
   :body-shell/press-die-mass-kg :body-shell/sim-peak-forming-force-n :body-shell/sim-peak-forming-pressure-mpa
   :body-shell/overall-length-actual-mm :body-shell/overall-length-min-mm :body-shell/overall-length-max-mm
   :body-shell/weld-quality-defect-unresolved? :body-shell/robotics-sim-verified? :body-shell/robotics-sim-record
   :body-shell/body-shell-shipped? :body-shell/body-certified?
   :body-shell/jurisdiction :body-shell/status :body-shell/shipment-number :body-shell/certificate-number])

(defn- pull->body-shell [m]
  (when (:body-shell/id m)
    {:id (:body-shell/id m) :shell-name (:body-shell/shell-name m)
     :rail-material-grade (:body-shell/rail-material-grade m)
     :press-die-mass-kg (:body-shell/press-die-mass-kg m)
     :sim-peak-forming-force-n (:body-shell/sim-peak-forming-force-n m)
     :sim-peak-forming-pressure-mpa (:body-shell/sim-peak-forming-pressure-mpa m)
     :overall-length-actual-mm (:body-shell/overall-length-actual-mm m)
     :overall-length-min-mm (:body-shell/overall-length-min-mm m)
     :overall-length-max-mm (:body-shell/overall-length-max-mm m)
     :weld-quality-defect-unresolved? (boolean (:body-shell/weld-quality-defect-unresolved? m))
     :robotics-sim-verified? (boolean (:body-shell/robotics-sim-verified? m))
     :robotics-sim-record (dec* (:body-shell/robotics-sim-record m))
     :body-shell-shipped? (boolean (:body-shell/body-shell-shipped? m))
     :body-certified? (boolean (:body-shell/body-certified? m))
     :jurisdiction (:body-shell/jurisdiction m) :status (:body-shell/status m)
     :shipment-number (:body-shell/shipment-number m) :certificate-number (:body-shell/certificate-number m)}))

(defrecord DatomicStore [conn]
  Store
  (body-shell [_ id]
    (pull->body-shell (d/pull (d/db conn) body-shell-pull [:body-shell/id id])))
  (all-body-shells [_]
    (->> (d/q '[:find [?id ...] :where [?e :body-shell/id ?id]] (d/db conn))
         (map #(pull->body-shell (d/pull (d/db conn) body-shell-pull [:body-shell/id %])))
         (sort-by :id)))
  (weld-quality-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :weld-quality-screen/body-shell-id ?aid] [?k :weld-quality-screen/payload ?p]]
              (d/db conn) id)))
  (material-cert-verification-of [_ body-shell-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/body-shell-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) body-shell-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (shipment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :shipment/seq ?s] [?e :shipment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (certificate-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :certificate/seq ?s] [?e :certificate/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-shipment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :shipment-sequence/jurisdiction ?j] [?e :shipment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-certificate-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :certificate-sequence/jurisdiction ?j] [?e :certificate-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (body-shell-already-shipped? [s body-shell-id]
    (boolean (:body-shell-shipped? (body-shell s body-shell-id))))
  (body-shell-already-certified? [s body-shell-id]
    (boolean (:body-certified? (body-shell s body-shell-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :body-shell/upsert
      (d/transact! conn [(body-shell->tx value)])

      :material-cert-verification/set
      (d/transact! conn [{:verification/body-shell-id (first path) :verification/payload (enc payload)}])

      :weld-quality-screen/set
      (d/transact! conn [{:weld-quality-screen/body-shell-id (first path) :weld-quality-screen/payload (enc payload)}])

      :body-shell/mark-shipped
      (let [body-shell-id (first path)
            {:keys [result body-shell-patch]} (ship-body-shell! s body-shell-id)
            jurisdiction (:jurisdiction (body-shell s body-shell-id))
            next-n (inc (next-shipment-sequence s jurisdiction))]
        (d/transact! conn
                     [(body-shell->tx (assoc body-shell-patch :id body-shell-id))
                      {:shipment-sequence/jurisdiction jurisdiction :shipment-sequence/next next-n}
                      {:shipment/seq (count (shipment-history s)) :shipment/record (enc (get result "record"))}])
        result)

      :body-shell/mark-certified
      (let [body-shell-id (first path)
            {:keys [result body-shell-patch]} (issue-body-certificate! s body-shell-id)
            jurisdiction (:jurisdiction (body-shell s body-shell-id))
            next-n (inc (next-certificate-sequence s jurisdiction))]
        (d/transact! conn
                     [(body-shell->tx (assoc body-shell-patch :id body-shell-id))
                      {:certificate-sequence/jurisdiction jurisdiction :certificate-sequence/next next-n}
                      {:certificate/seq (count (certificate-history s)) :certificate/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-body-shells [s body-shells]
    (when (seq body-shells) (d/transact! conn (mapv body-shell->tx (vals body-shells)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:body-shells ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [body-shells]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-body-shells s body-shells))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo body-shell set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
