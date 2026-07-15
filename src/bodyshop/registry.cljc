(ns bodyshop.registry
  "Pure-function body-shell-shipment + Body-in-White Quality
  Certificate record construction -- an append-only body-shop
  book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a body-shell-shipment or
  body-certificate reference number -- every plant assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `bodyshop.facts` uses.

  `body-shell-dimension-out-of-range?` continues this fleet's
  two-sided range check family (`testlab.registry/within-tolerance?`
  established the first, `conservation.registry/body-condition-out-
  of-range?` the second, `water.registry/contaminant-level-out-of-
  range?` the third, `steelworks.registry/heat-chemistry-out-of-
  range?`/`turbine.registry/unit-tolerance-out-of-range?`/`automotive.
  registry/vehicle-emissions-out-of-range?`/`autoparts.registry/part-
  lot-dppm-out-of-range?` further siblings), applying the SAME
  lo/hi bounds-comparison shape to a body-shell's own measured
  overall-length dimensional deviation against the body-shell's own
  recorded nominal-body-dimension spec bounds -- a real end-of-line
  dimensional-QA metric (CMM/optical-scan overall-length measurement
  against nominal), distinct from `bodyshop.robotics`'s own
  forming-pressure ceiling check (a physics-derived process reading,
  not a finished-part dimensional measurement).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant/MES control system. It builds the RECORD a
  body shop would keep, not the act of shipping the body-shell robot
  action or issuing the body certificate itself (that is `bodyshop.
  operation`'s `:actuation/ship-body-shell`/`:actuation/issue-body-
  certificate`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the body-shop plant's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn body-shell-dimension-out-of-range?
  "Does `body-shell`'s own `:overall-length-actual-mm` fall outside
  its own `[:overall-length-min-mm :overall-length-max-mm]` recorded
  nominal-body-dimension spec bounds? A pure ground-truth check
  against the body-shell's own permanent fields -- no upstream
  comparison needed. A further sibling in this fleet's two-sided
  range check family (see ns docstring)."
  [{:keys [overall-length-actual-mm overall-length-min-mm overall-length-max-mm]}]
  (and (number? overall-length-actual-mm) (number? overall-length-min-mm) (number? overall-length-max-mm)
       (or (< overall-length-actual-mm overall-length-min-mm)
           (> overall-length-actual-mm overall-length-max-mm))))

(defn register-body-shell-shipment
  "Validate + construct the BODY-SHELL-SHIPMENT registration DRAFT --
  the body shop's own act of dispatching a real robot shipment/
  handling action to release a welded body-in-white shell onward to
  the final-assembly plant (the real upstream -> downstream hand-off
  to `cloud-itonami-isic-2910`'s own `:vehicle/intake` -- see README).
  Pure function -- does not touch any real plant/MES control system;
  it builds the RECORD a body shop would keep. `bodyshop.governor`
  independently re-verifies the body-shell's own dimensional
  sufficiency against its own spec bounds, and a double-shipment for
  the same body-shell, before this is ever allowed to commit."
  [body-shell-id jurisdiction sequence]
  (when-not (and body-shell-id (not= body-shell-id ""))
    (throw (ex-info "body-shell-shipment: body_shell_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "body-shell-shipment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "body-shell-shipment: sequence must be >= 0" {})))
  (let [shipment-number (str (str/upper-case jurisdiction) "-BSH-" (zero-pad sequence 6))
        record {"record_id" shipment-number
                "kind" "body-shell-shipment-draft"
                "body_shell_id" body-shell-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "shipment_number" shipment-number
     "certificate" (unsigned-certificate "BodyShellShipment" shipment-number shipment-number)}))

(defn register-body-certificate
  "Validate + construct the BODY-IN-WHITE QUALITY CERTIFICATE
  registration DRAFT -- the body shop's own act of issuing a real
  body-shop QA certificate certifying a body-shell's dimensional/weld/
  material conformance before onward shipment. Pure function -- does
  not touch any real plant/MES control system; it builds the RECORD a
  body shop would keep. `bodyshop.governor` independently re-verifies
  the body-shell's own weld-quality-defect resolution status, and a
  double-issuance for the same body-shell, before this is ever allowed
  to commit."
  [body-shell-id jurisdiction sequence]
  (when-not (and body-shell-id (not= body-shell-id ""))
    (throw (ex-info "body-certificate: body_shell_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "body-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "body-certificate: sequence must be >= 0" {})))
  (let [certificate-number (str (str/upper-case jurisdiction) "-BIWQC-" (zero-pad sequence 6))
        record {"record_id" certificate-number
                "kind" "body-certificate-draft"
                "body_shell_id" body-shell-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certificate_number" certificate-number
     "certificate" (unsigned-certificate "BodyInWhiteQualityCertificate" certificate-number certificate-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
