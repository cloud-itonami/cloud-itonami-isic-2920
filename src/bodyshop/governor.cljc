(ns bodyshop.governor
  "Stamping Governor -- the independent compliance layer that earns
  the Body Shop Advisor the right to commit. The LLM has no notion of
  material-certification evidence law, whether a body-shell's own
  measured overall-length dimensional deviation actually stays within
  its own recorded nominal-body-dimension spec bounds, whether an
  end-of-line weld-quality defect against the body-shell has actually
  stayed unresolved, or when an act stops being a draft and becomes a
  real-world robot body-shell shipment or Body-in-White Quality
  Certificate issuance, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD -- the body-shop analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated material-cert spec-basis, incomplete evidence, a robot
  stamping-press-forming simulation that never ran or that
  independently re-checks out-of-tolerance, an out-of-spec body-shell
  dimension, an unresolved weld-quality defect, or a double shipment/
  certificate-issuance). The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `bodyshop.phase`: for `:stake :actuation/ship-
  body-shell`/`:actuation/issue-body-certificate` (a real
  safety-critical act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the material-cert-rules
                                       evidence proposal cite an
                                       OFFICIAL source
                                       (`bodyshop.facts`), or invent
                                       one?
    2. Evidence incomplete         -- for `:actuation/ship-body-
                                       shell`/`:actuation/issue-body-
                                       certificate`, has the body-shell
                                       actually been verified with a
                                       full material-cert evidence
                                       checklist (mill test report/
                                       chemical composition
                                       certificate/coating designation
                                       certificate/formability test
                                       report) on file?
    3. Robot simulation missing or
       independently out-of-
       tolerance                    -- for `:actuation/ship-body-
                                       shell`, has the robot stamping-
                                       press-forming verification
                                       mission (`bodyshop.robotics`)
                                       actually run and been recorded
                                       on the body-shell
                                       (`:robotics-sim-verified?`)? AND
                                       INDEPENDENTLY recompute whether
                                       the body-shell's own recorded
                                       REAL `physics-2d`-simulated
                                       forming-pressure telemetry
                                       (`:sim-peak-forming-pressure-
                                       mpa`, from ADR-2607152000's real
                                       time-stepped simulation) exceeds
                                       its own rail-material-grade's
                                       real forming-pressure ceiling
                                       (`bodyshop.robotics/simulation-
                                       out-of-tolerance?`), ignoring
                                       whatever :passed? verdict the
                                       mission run itself stored -- the
                                       same 'ground truth, not
                                       self-report' discipline check 4
                                       below uses for dimension.
    4. Body-shell dimension out of
       range                        -- for `:actuation/ship-body-
                                       shell`, INDEPENDENTLY recompute
                                       whether the body-shell's own
                                       measured overall-length falls
                                       outside its own recorded
                                       nominal-body-dimension spec
                                       bounds (`bodyshop.registry/
                                       body-shell-dimension-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. A further
                                       instance of this fleet's
                                       two-sided range check family
                                       (see `bodyshop.registry`'s ns
                                       docstring for the lineage).
    5. Weld-quality defect
       unresolved                    -- reported by THIS proposal
                                       itself (an `:end-of-line-
                                       quality/screen` that just found
                                       an unresolved defect), or
                                       already on file for the
                                       body-shell (`:end-of-line-
                                       quality/screen`/`:actuation/
                                       issue-body-certificate`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       `automotive.governor/end-of-
                                       line-defect-unresolved-
                                       violations`/`autoparts.governor/
                                       process-capability-defect-
                                       unresolved-violations` (prior
                                       siblings) established --
                                       exercised in tests/demo via
                                       `:end-of-line-quality/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened
                                       body-shell -- see this ns's own
                                       test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/ship-
                                       body-shell`/`:actuation/issue-
                                       body-certificate` (REAL
                                       safety-critical acts) ->
                                       escalate.

  Two more guards, double-shipment/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-shipped-violations`/`already-certified-violations` refuse
  to ship a body-shell action/issue a Body-in-White Quality
  Certificate for the SAME body-shell twice, off dedicated `:body-
  shell-shipped?`/`:body-certified?` facts (never a `:status` value)
  -- the SAME 'check a dedicated boolean, not status' discipline every
  prior sibling governor's guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [bodyshop.facts :as facts]
            [bodyshop.registry :as registry]
            [bodyshop.robotics :as robotics]
            [bodyshop.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Shipping a real body-shell onward to final assembly and issuing a
  real Body-in-White Quality Certificate are the two real-world
  actuation events this actor performs -- a two-member set, matching
  every prior dual-actuation sibling's shape."
  #{:actuation/ship-body-shell :actuation/issue-body-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:material-cert-rules/verify` (or actuation) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's material-certification requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:material-cert-rules/verify :actuation/ship-body-shell :actuation/issue-body-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は材料証明要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/ship-body-shell`/`:actuation/issue-body-
  certificate`, the jurisdiction's required material-cert evidence
  (mill test report/chemical composition certificate/coating
  designation certificate/formability test report) must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/ship-body-shell :actuation/issue-body-certificate} op)
    (let [a (store/body-shell st subject)
          verification (store/material-cert-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要材料証明書類(ミルシート/化学成分証明書/めっき仕様証明書/成形性試験報告書等)が充足していない状態での提案"}]))))

(defn- robotics-simulation-violations
  "For `:actuation/ship-body-shell`: HARD hold if the robot stamping-
  press-forming verification mission (`bodyshop.robotics`) never ran
  and was recorded on the body-shell (`:robotics-sim-verified?`), OR
  if it did but an INDEPENDENT recompute of the body-shell's own REAL
  `physics-2d`-simulated forming-pressure telemetry
  (`:sim-peak-forming-pressure-mpa`, ADR-2607152000 --
  `bodyshop.robotics/simulation-out-of-tolerance?`) says
  out-of-tolerance right now -- never trusts the mission's own stored
  :passed? verdict alone, the same discipline
  `body-shell-dimension-out-of-range-violations` below uses for
  dimension."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-body-shell)
    (let [a (store/body-shell st subject)]
      (cond
        (not (:robotics-sim-verified? a))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のスタンピングプレス成形ロボット検証ミッションが未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? a)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の実測成形圧力(" (:sim-peak-forming-pressure-mpa a)
                       "MPa)が独立再検証で材料グレード(" (:rail-material-grade a)
                       ")の許容上限(" (robotics/forming-pressure-ceiling-mpa (:rail-material-grade a)) "MPa)を超過")}]))))

(defn- body-shell-dimension-out-of-range-violations
  "For `:actuation/ship-body-shell`, INDEPENDENTLY recompute whether
  the body-shell's own overall-length falls outside its own recorded
  nominal-body-dimension spec bounds via `bodyshop.registry/body-
  shell-dimension-out-of-range?` -- needs no proposal inspection or
  stored-verdict lookup at all, since its inputs are permanent
  ground-truth fields already on the body-shell."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-body-shell)
    (let [a (store/body-shell st subject)]
      (when (registry/body-shell-dimension-out-of-range? a)
        [{:rule :body-shell-dimension-out-of-range
          :detail (str subject " の実測全長(" (:overall-length-actual-mm a)
                      "mm)が公称寸法範囲[" (:overall-length-min-mm a) "," (:overall-length-max-mm a) "]mmを逸脱")}]))))

(defn- weld-quality-defect-unresolved-violations
  "An unresolved end-of-line weld/dimensional-quality defect --
  reported by THIS proposal (e.g. an `:end-of-line-quality/screen`
  that itself just found one), or already on file in the store for the
  body-shell (`:end-of-line-quality/screen`/`:actuation/issue-body-
  certificate`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        body-shell-id (when (contains? #{:end-of-line-quality/screen :actuation/issue-body-certificate} op) subject)
        hit-on-file? (and body-shell-id (= :unresolved (:verdict (store/weld-quality-screen-of st body-shell-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :weld-quality-defect-unresolved
        :detail "未解決の溶接/寸法品質欠陥がある状態でのボディ証明書発行提案は進められない"}])))

(defn- already-shipped-violations
  "For `:actuation/ship-body-shell`, refuses to ship a body-shell
  action for the SAME body-shell twice, off a dedicated `:body-shell-
  shipped?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/ship-body-shell)
    (when (store/body-shell-already-shipped? st subject)
      [{:rule :already-shipped
        :detail (str subject " は既に出荷実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-body-certificate`, refuses to issue a
  Body-in-White Quality Certificate for the SAME body-shell twice, off
  a dedicated `:body-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-body-certificate)
    (when (store/body-shell-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既にボディ証明書発行済み")}])))

(defn check
  "Censors a Body Shop Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (robotics-simulation-violations request st)
                           (body-shell-dimension-out-of-range-violations request st)
                           (weld-quality-defect-unresolved-violations request proposal st)
                           (already-shipped-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
