(ns bodyshop.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave3 rollout): this repo previously shipped a half-finished generator
  that never ran the robot stamping-press mission before shipment
  (so shell-1 always HARD-held on `:robotics-simulation-missing`),
  invented shell-999 / HSLA-950 material fields the store does not own,
  and named a non-existent op `:actuation/issue-stamping-certificate`.
  This namespace drives the REAL actor stack
  (`bodyshop.operation` -> `bodyshop.governor` -> `bodyshop.store`)
  through a scenario adapted from this repo's own `bodyshop.sim` demo
  driver (`clojure -M:dev:run`, confirmed BEFORE writing this file to
  produce a sensible ledger against the real seeded body-shell ids
  `shell-1`..`shell-5` -- those ids match `bodyshop.store/demo-data`,
  so it was safe to reuse rather than author from scratch), covering
  one full intake -> material-cert verify -> end-of-line screen ->
  stamping-press robotics mission -> ship-body-shell ->
  issue-body-certificate lifecycle plus every distinct HARD-hold reason
  this governor defends, rendered deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verify by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [bodyshop.store :as store]
            [bodyshop.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :quality-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: shell-1 clears a full lifecycle -- intake
  (auto-commit clean at phase 3, no capital risk), an early ship attempt
  that HARD-holds for incomplete material-cert evidence (never reaches a
  human), material-cert verification (phase-gated -- not yet
  auto-eligible -- approved), end-of-line weld-quality screen
  (approved), robot stamping-press-forming mission (approved; real
  physics-2d), body-shell shipment (ALWAYS escalates --
  `:actuation/ship-body-shell` is permanently high-stakes, never auto
  at any phase -- approved) and Body-in-White Quality Certificate
  issuance (ALWAYS escalates -- `:actuation/issue-body-certificate`,
  same posture -- approved); shell-2 HARD-holds a material-cert
  verification with no official spec-basis for its (deliberately
  unregistered) jurisdiction ATL; shell-3 clears verification
  (approved) then HARD-holds a ship before robotics ran, clears the
  robotics mission (approved), then HARD-holds a ship whose measured
  overall-length (4720mm) falls outside its own recorded bounds
  [4650,4680]mm; shell-5 clears material-cert (approved) then HARD-
  holds a ship whose real physics-2d-rechecked forming pressure
  exceeds its DP600 ceiling (seeded with an oversized press-die mass
  despite `:robotics-sim-verified? true` already on file); shell-4
  HARD-holds an end-of-line weld-quality screen that itself detects an
  unresolved defect; shell-1 then HARD-holds a second shipment
  (`:already-shipped`) and a second certificate (`:already-certified`).
  Every HARD hold never reaches a human. Returns the resulting store --
  every field read by `render` below is real governor/store output, not
  a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    ;; shell-1 full lifecycle
    (exec! actor "t1" {:op :body-shell/intake :subject "shell-1"
                       :patch {:id "shell-1"
                               :shell-name "Meridian Sedan Body Shell BS-3301"}})

    (exec! actor "t1b" {:op :actuation/ship-body-shell :subject "shell-1"})

    (exec! actor "t2" {:op :material-cert-rules/verify :subject "shell-1"})
    (approve! actor "t2")

    (exec! actor "t3" {:op :end-of-line-quality/screen :subject "shell-1"})
    (approve! actor "t3")

    (exec! actor "t3b" {:op :robotics/simulate-stamping-press :subject "shell-1"})
    (approve! actor "t3b")

    (exec! actor "t4" {:op :actuation/ship-body-shell :subject "shell-1"})
    (approve! actor "t4")

    (exec! actor "t5" {:op :actuation/issue-body-certificate :subject "shell-1"})
    (approve! actor "t5")

    ;; shell-2: no official material-cert spec-basis (jurisdiction ATL)
    (exec! actor "t6" {:op :material-cert-rules/verify :subject "shell-2"})

    ;; shell-3: robotics missing, then dimension out of range
    (exec! actor "t7" {:op :material-cert-rules/verify :subject "shell-3"})
    (approve! actor "t7")

    (exec! actor "t7b" {:op :actuation/ship-body-shell :subject "shell-3"})

    (exec! actor "t7c" {:op :robotics/simulate-stamping-press :subject "shell-3"})
    (approve! actor "t7c")

    (exec! actor "t8" {:op :actuation/ship-body-shell :subject "shell-3"})

    ;; shell-5: robotics-sim on file but independent recheck fails
    (exec! actor "t8b" {:op :material-cert-rules/verify :subject "shell-5"})
    (approve! actor "t8b")

    (exec! actor "t8c" {:op :actuation/ship-body-shell :subject "shell-5"})

    ;; shell-4: unresolved weld-quality defect
    (exec! actor "t9" {:op :end-of-line-quality/screen :subject "shell-4"})

    ;; shell-1 double-actuation guards
    (exec! actor "t10" {:op :actuation/ship-body-shell :subject "shell-1"})
    (exec! actor "t11" {:op :actuation/issue-body-certificate :subject "shell-1"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (-> f :violations first :rule)
                     (first (:basis f)))]
        (str "<span class=\"critical\">HARD hold &middot; "
             (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- dimension-cell [{:keys [overall-length-actual-mm
                               overall-length-min-mm
                               overall-length-max-mm]}]
  (if (and (number? overall-length-actual-mm)
           (number? overall-length-min-mm)
           (number? overall-length-max-mm)
           (<= overall-length-min-mm overall-length-actual-mm overall-length-max-mm))
    (format "<span class=\"ok\">%s mm &isin; [%s,%s]</span>"
            (esc overall-length-actual-mm)
            (esc overall-length-min-mm)
            (esc overall-length-max-mm))
    (format "<span class=\"err\">%s mm out of [%s,%s]</span>"
            (esc overall-length-actual-mm)
            (esc overall-length-min-mm)
            (esc overall-length-max-mm))))

(defn- weld-cell [{:keys [weld-quality-defect-unresolved?]}]
  (if weld-quality-defect-unresolved?
    "<span class=\"err\">unresolved</span>"
    "<span class=\"ok\">resolved</span>"))

(defn- robotics-cell [{:keys [robotics-sim-verified?]}]
  (if robotics-sim-verified?
    "<span class=\"ok\">on file</span>"
    "<span class=\"muted\">not run</span>"))

(defn- lifecycle-cell [{:keys [body-shell-shipped? body-certified?]}]
  (cond
    (and body-shell-shipped? body-certified?)
    "<span class=\"ok\">shipped &amp; certified</span>"
    body-shell-shipped?
    "<span class=\"warn\">shipped, not certified</span>"
    body-certified?
    "<span class=\"warn\">certified, not shipped</span>"
    :else "<span class=\"muted\">in intake / verification</span>"))

(defn- shell-row [ledger {:keys [id shell-name jurisdiction rail-material-grade] :as shell}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id)
          (esc shell-name)
          (esc jurisdiction)
          (esc (name (or rail-material-grade :unknown)))
          (dimension-cell shell)
          (weld-cell shell)
          (robotics-cell shell)
          (lifecycle-cell shell)
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t))
          (esc (name (or op :n-a)))
          (esc subject)
          (esc (or (some->> basis
                            (map #(if (keyword? %) (name %) %))
                            (str/join ", "))
                   (some-> disposition name)
                   ""))))

(defn- draft-row [prefix {:strs [record_id body_shell_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix)
          (esc record_id)
          (esc body_shell_id)
          (esc jurisdiction)
          (if immutable
            "<span class=\"ok\">immutable draft</span>"
            (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops`, `bodyshop.governor`/`bodyshop.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:body-shell/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet</span></td></tr>"
   "        <tr><td><code>:material-cert-rules/verify</code></td><td><span class=\"warn\">phase-3: human approval (not yet auto-eligible)</span> &middot; HARD hold on missing official AHSS material-cert spec-basis</td></tr>"
   "        <tr><td><code>:end-of-line-quality/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean</span> &middot; an unresolved weld-quality defect is a HARD, un-overridable hold instead</td></tr>"
   "        <tr><td><code>:robotics/simulate-stamping-press</code></td><td><span class=\"warn\">ALWAYS human approval</span> &middot; real physics-2d press-die/blank forming mission; required on file before shipment</td></tr>"
   "        <tr><td><code>:actuation/ship-body-shell</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; evidence + robotics-sim + independent dimension/pressure recheck &middot; double-shipment refused</td></tr>"
   "        <tr><td><code>:actuation/issue-body-certificate</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; material-cert evidence + double-issuance refused</td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        shells (store/all-body-shells db)
        shell-rows (str/join "\n" (map (partial shell-row ledger) shells))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        shipment-rows (str/join "\n" (map (partial draft-row "body-shell-shipment")
                                          (store/shipment-history db)))
        cert-rows (str/join "\n" (map (partial draft-row "body-certificate")
                                      (store/certificate-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-2920 &middot; body-in-white stamping/welding body shop</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Body-in-white stamping/welding body shop (ISIC 2920) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · ship/certificate always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Body shells</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>bodyshop.store</code> via <code>bodyshop.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. No invented numbers.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Shell</th><th>Name</th><th>Jurisdiction</th><th>Rail grade</th><th>Overall length</th><th>Weld quality</th><th>Robotics sim</th><th>Lifecycle</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     shell-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft shipment / Body-in-White Quality Certificate records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the body-shop plant's own act of releasing a real BIW shell to final assembly (ISIC 2910) or filing a real quality certificate is outside this actor's authority (see README <code>Actuation honesty</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Body shell</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     shipment-rows (when (seq shipment-rows) "\n")
     cert-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Stamping Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Spec-basis, evidence completeness, robot stamping-press mission presence, independent forming-pressure recheck, body-shell dimension range, unresolved weld-quality defects, and double shipment/certificate issuance are independently recomputed, never trusted from the proposal; a real body-shell shipment or Body-in-White Quality Certificate is always a human quality engineer's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced. Social hand-off: <code>bodyshop.export/audit-package</code> and <code>package-&gt;csv-bundle</code> (body-shells / ledger / shipments / body-certificates CSV).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        out-file (java.io.File. out)]
    (when-let [parent (.getParentFile out-file)]
      (.mkdirs parent))
    (spit out-file html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/shipment-history db)) "body-shell-shipment drafts,"
             (count (store/certificate-history db)) "body-certificate drafts )")))
