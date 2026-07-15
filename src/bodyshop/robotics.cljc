(ns bodyshop.robotics
  "Robot-executed stamping-press forming verification -- the concrete,
  actor-level realization of ADR-2607011000's robotics premise and
  ADR-2607142800's robotics-process-simulation pattern, delivered
  DIRECTLY onto ADR-2607152000's real-physics fleet extension (this
  vertical, isic-2920, was not one of ADR-2607152000's original 6
  follow-up verticals -- it is a NEW actor built to that same standard
  from day one, mirroring how `cloud-itonami-isic-2930`/`cloud-itonami-
  isic-2394` deliver it natively rather than retrofitted).

  A genuine time-stepped `kotoba-lang/physics-2d` rigid-body
  simulation of a SHEET-METAL STAMPING PRESS forming a body panel: a
  press-die `Body2D` (the press's moving upper tool + slide assembly)
  closes at a controlled velocity onto a static (mass 0, immovable --
  the same `vdesign.simphysics`/`cementmill.robotics`/`autoparts.
  robotics` crash-barrier/specimen/fixture pattern) sheet-metal-blank
  `Body2D`. `world-step` actually integrates/collides/resolves the
  contact over real ticks; `:sim-peak-forming-force-n` is read
  directly off the ACTUAL simulated velocity trajectory (F = m*a, the
  SAME technique every real-physics sibling in this fleet uses), and
  `:sim-peak-forming-pressure-mpa` divides that force by a real,
  disclosed local die-contact-patch area to give a reading directly
  comparable to the panel's own rail-material-grade yield strength.

  HONEST REINTERPRETATION TECHNIQUE: unlike autoparts' pull-test
  (which reframes a SEPARATING event as an approach against a virtual
  limit-boundary), a stamping press genuinely IS a closing/colliding
  event -- press-die approaches and contacts the blank -- so this ns
  needs no such reframing; it is the SAME collision shape
  `cementmill.robotics`'s compressive-strength press models,
  substituting a sheet-metal blank for a cube specimen.

  Disclosed engineering priors (this ns's own, not measured facts --
  same discipline as automotive's `frontal-area-m2` table / autoparts'
  `min-proof-load-n`):

  - `press-closing-velocity-mps` is a disclosed ANALOG closing rate,
    not a literal transcription of any specific press's stroke-speed
    curve. Real mechanical/servo stamping presses' slide velocity
    approaching bottom-dead-center for a working (forming) stroke on
    a large structural body panel commonly falls in roughly the
    0.3-1.5 m/s range depending on press size/type/SPM (strokes per
    minute) -- 1.0 m/s sits mid-range. `physics-2d`'s impulse resolver
    has NO progressive crush-stiffness/force-deflection model (the
    SAME disclosed limitation every real-physics sibling states):
    whatever tick first detects ANY AABB overlap fully zeroes the
    closing velocity in that ONE tick (restitution 0) -- a discrete,
    instantaneous stop, not a continuous stamping stroke's actual
    force-vs-displacement curve.
  - `draw-depth-m` (80 mm) is a representative deep-draw depth for a
    structural automotive body panel -- real deep-drawn panels
    (fenders, doors, hoods, quarter panels) commonly draw in the
    50-150 mm range; 80 mm sits mid-range. This is the post-contact
    'give' distance used to derive `dt` (the per-tick timestep), the
    SAME principled-not-arbitrary identity `vdesign.simphysics`/
    `cementmill.robotics`/`autoparts.robotics` use for their own `dt`.
  - `local-die-contact-side-mm` (40 mm, giving a 1600 mm^2 square
    local-contact-patch area) is a DISCLOSED SIMPLIFICATION: real
    metal-forming unit-pressure checks (e.g. against a forming-limit
    diagram) compare a LOCAL contact-zone pressure, not an average
    over the whole irregular panel face -- this ns models that local
    zone as a square patch with side equal to the modeled AABB
    colliders' own lateral (`half-h`) extent, an honest simplification
    for a general panel shape (unlike `cementmill.robotics`'s ASTM
    C109 cube, which has a REAL standard square face -- this vertical
    has no equivalently standardized local-contact geometry to cite,
    so the square-patch choice is disclosed as this ns's own modeling
    convenience, not a cited standard).
  - By exact kinematic identity (a = v^2/d for a boxcar full stop over
    transit distance d at speed v), `press-die-mass-kg` is the ONLY
    quantity that scales `:sim-peak-forming-force-n`/`:sim-peak-
    forming-pressure-mpa` for a fixed closing velocity/draw depth --
    the peak deceleration itself is INDEPENDENT of the press-die's own
    mass when colliding with a mass-0 (immovable) blank (mass cancels
    algebraically in `physics-2d`'s `resolve-contact`, the SAME
    verified property every real-physics sibling in this fleet
    establishes). A real large structural-panel transfer-press slide +
    upper-die-set assembly plausibly weighs on the order of tens to a
    few hundred tonnes -- the `press-die-mass-kg` values this ns's
    demo data uses (tens of thousands of kg) sit in that real order of
    magnitude, not an arbitrary unit-less number.

  `panel-yield-strength-mpa` seeds REAL, published (range-midpoint,
  disclosed as such) yield-strength anchors for the SAME real AHSS
  grades `kami-engine-vehicle-designer`'s `vdesign.simverify` geometry
  map already cites for automotive's OWN crash structural model
  (`:DP600`/`:DP980`/`:boron-PHS`) -- deliberate vocabulary reuse for
  genuine cross-actor consistency: THIS actor's body-shell rail
  material grade is the literal input to `cloud-itonami-isic-2910`'s
  own downstream crash-structural check (see README `Upstream ->
  downstream hand-off`).
    - `:DP600` -- SAE J2340 dual-phase designation; published yield
      strength commonly cited in the ~340-420 MPa range. This ns uses
      380 MPa (the range midpoint).
    - `:DP980` -- SAE J2340 dual-phase designation; published yield
      strength commonly cited in the ~550-750 MPa range. This ns uses
      650 MPa (the range midpoint).
    - `:boron-PHS` -- hot-stamped/press-hardened boron steel (e.g.
      22MnB5, marketed as Usibor(R) 1500 and similar); published
      POST-QUENCH yield strength commonly cited in the ~1000-1200 MPa
      range. This ns uses 1100 MPa (the range midpoint).

  `forming-pressure-ceiling-multiple` (3.0x the panel's own nominal
  yield strength) is a NEWLY-DEFINED, clearly-disclosed bound
  (ADR-2607152000 explicitly allows this when no existing on-file
  field/cited standard fits a FORMING-PRESSURE reading better): real
  stamping DOES require forming pressure to exceed a panel's yield
  strength locally (that IS how plastic deformation happens -- a
  forming pressure below yield would mean the panel sprang back
  without permanently forming); a forming pressure spiking far beyond
  a multiple-of-yield severity envelope is used here as an honest
  proxy for a forming-limit-diagram exceedance (excessive local
  thinning / tear risk), NOT a literal transcription of one specific
  named standard's numeric tear threshold.

  `forming-pressure-out-of-tolerance?` independently re-derives the
  body-shell's OWN recorded `:sim-peak-forming-pressure-mpa` against
  its OWN rail-material-grade's `forming-pressure-ceiling-mpa`, never
  from the mission's self-reported result -- the SAME 'ground truth,
  not self-report' discipline every real-physics sibling in this fleet
  applies. `bodyshop.governor`'s `robotics-simulation-violations` calls
  this ns's independent recheck, never the stored :passed? value,
  before any `:actuation/ship-body-shell` proposal may commit.

  Pure data + pure functions -- no real robot I/O, no network.
  `physics-2d/world-step` is itself a pure, fixed-timestep integrator
  (no wall-clock/IO), so this stays exactly as offline/deterministic
  as every other sibling namespace in this actor -- tests and the demo
  run without a network.

  Honest scope (mirrors every real-physics sibling's own disclosure):
  this DOES model a real time-stepped `physics-2d` rigid-body
  trajectory for the press-forming event. It does NOT model: sheet-
  metal material stiffness/stress-strain or a force-vs-displacement
  crush curve (`physics-2d` has no such model at all), 3D geometry (2D
  projection only), springback, a real load-cell/DAQ connection, or a
  real press-controller/servo-motion-planning system -- still
  simulation, not control, the same 'policy, not control' boundary
  `kotoba.robotics`'s docstring already establishes."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

;; ---------------------------------------------------------------------------
;; Platform shims (mirrors physics-2d's/autoparts.robotics's own private
;; sqrt*/abs*/ceil* style, keeping this ns portable .cljc).
;; ---------------------------------------------------------------------------

(defn- abs* [x] (if (neg? x) (- x) x))

(defn- ceil* [x]
  #?(:clj  (Math/ceil (double x))
     :cljs (js/Math.ceil x)))

(def mission-actions
  "The three-step stamping-press/quality-cell verification mission
  every body-shell walks through before `:actuation/ship-body-shell`
  is proposable. :grasp/:actuate at :low safety, :sense at :none --
  verification/QA handling of a stationary body-shell, not the
  moving-shipment actuation that is `:actuation/ship-body-shell`
  itself (always :safety-critical -- see `bodyshop.governor`)."
  [{:step :sheet-metal-blank-loading         :kind :grasp   :safety :low}
   {:step :stamping-press-forming-cycle      :kind :actuate :safety :low}
   {:step :dimensional-cmm-scan              :kind :sense   :safety :none}])

;; ---------------------- real, cited material constants ----------------------

(def panel-yield-strength-mpa
  "Real, published (range-midpoint, disclosed) yield-strength anchors
  for the SAME AHSS grades `vdesign.simverify`'s geometry map already
  cites for automotive's own crash structural model -- see ns
  docstring for the exact published ranges each midpoint is drawn
  from."
  {:DP600 380.0
   :DP980 650.0
   :boron-PHS 1100.0})

(def ^:const forming-pressure-ceiling-multiple
  "NEWLY-DEFINED, disclosed multiple of a panel's own nominal yield
  strength above which a simulated forming pressure is treated as an
  anomalously severe stamping event (excessive local thinning/tear
  risk) -- see ns docstring."
  3.0)

(defn forming-pressure-ceiling-mpa
  "The real, disclosed forming-pressure ceiling (MPa) for `grade` --
  `forming-pressure-ceiling-multiple` x that grade's own
  `panel-yield-strength-mpa`. nil for an unrecognized grade (never
  fabricates a ceiling for a grade this ns has no anchor for)."
  [grade]
  (when-let [y (get panel-yield-strength-mpa grade)]
    (* forming-pressure-ceiling-multiple y)))

;; ---------------------- real physics-2d press constants ---------------------

(def ^:const press-closing-velocity-mps
  "Controlled press-die closing velocity (m/s) -- see ns docstring: a
  disclosed ANALOG rate for a large structural-body-panel forming
  stroke, not a literal transcription of any one press's stroke-speed
  curve."
  1.0)

(def ^:const draw-depth-m
  "Representative deep-draw depth (m) for a structural automotive
  body panel -- see ns docstring."
  0.08)

(def ^:const dt
  "Per-tick timestep (s) -- derived from THIS simulation's own
  draw-depth/closing-velocity (the nominal transit time across the
  panel's own draw zone), the SAME principled-not-arbitrary identity
  every real-physics sibling uses for its own `dt`."
  (/ draw-depth-m press-closing-velocity-mps))

(def ^:const local-die-contact-side-mm
  "Local die-contact-patch side length (mm) -- see ns docstring: a
  disclosed square-patch simplification for computing a representative
  LOCAL unit forming pressure, not an average over the whole panel
  face."
  40.0)

(def ^:const local-die-contact-area-mm2
  "The local die-contact patch's own face area (mm^2). 1 MPa =
  1 N/mm^2, so dividing a simulated force (N) by this real, fixed
  geometry constant converts directly to a stress reading (MPa)
  comparable to the panel's own `panel-yield-strength-mpa`/
  `forming-pressure-ceiling-mpa`."
  (* local-die-contact-side-mm local-die-contact-side-mm))

(def ^:const die-half-w-m
  "Press-die AABB half-width (m) along the travel axis -- a thin,
  rigid die face; `physics-2d` colliders do not deform, so this
  dimension is a disclosed, arbitrary rigid-body stand-in, not a
  load-bearing physical parameter (mirrors `cementmill.robotics`'s
  `platen-half-w-m`)."
  0.01)

(def ^:const die-half-h-m
  "Press-die AABB half-height (m), lateral -- half of
  `local-die-contact-side-mm` (converted to metres), so the modeled
  local contact zone is exactly this die's own lateral footprint."
  (/ (/ local-die-contact-side-mm 1000.0) 2.0))

(def ^:const blank-half-w-m
  "Sheet-metal-blank AABB half-width (m) along the travel axis -- a
  disclosed rigid-body stand-in for the blank/draw-allowance
  interaction zone (NOT a literal sheet-steel thickness, which for
  automotive body sheet is typically ~0.6-2.0 mm -- `physics-2d`
  colliders do not deform, so this is a rigid-body geometry stand-in
  only, the same discipline every sibling's fixture/specimen dimension
  uses)."
  0.02)

(def ^:const blank-half-h-m
  "Sheet-metal-blank AABB half-height (m), lateral -- the same local
  contact-patch half-extent as `die-half-h-m`, so the WHOLE modeled
  local zone loads, matching how a real press's die is sized to fully
  cover its own local forming zone."
  die-half-h-m)

(def ^:const gap-m
  "Press standoff distance (m) the die starts behind the blank, so the
  trajectory captures a real pre-contact approach phase, not just the
  collision tick itself (mirrors every sibling's own gap constant)."
  0.05)

(def ^:const settle-ticks
  "Extra ticks appended after the die is expected to reach the blank,
  so the trajectory also captures post-contact settling -- the SAME
  constant + rationale as every real-physics sibling: `physics-2d`'s
  positional correction removes 80% of any remaining overlap per tick,
  so residual overlap after 15 more ticks is ~3e-11 of whatever it was
  at first contact."
  15)

;; ------------------------------ real simulation ------------------------------

(defn simulate-press
  "Time-steps a REAL `physics-2d` world for ONE stamping-press forming
  cycle: a press-die `Body2D` (mass `press-die-mass-kg`, velocity
  `press-closing-velocity-mps`) approaches and collides with a static
  (mass 0, immovable) sheet-metal-blank `Body2D`. Returns
  {:trajectory [{:tick :position :velocity} ...] (die body only)
  :sim-peak-forming-force-n n :sim-peak-forming-pressure-mpa n
  :sim-peak-draw-distance-m n :ticks n :dt n :closing-velocity-mps n}.

  `:sim-peak-forming-force-n` is `press-die-mass-kg` times the PEAK
  magnitude of tick-to-tick velocity change (along the travel axis)
  divided by `dt` -- F = m*a, derived from the ACTUAL simulated
  velocity trajectory. `:sim-peak-forming-pressure-mpa` divides that
  force by `local-die-contact-area-mm2` -- 1 MPa = 1 N/mm^2 -- so it
  is directly comparable to a body-shell's own rail-material-grade
  `panel-yield-strength-mpa`/`forming-pressure-ceiling-mpa`.
  `:sim-peak-draw-distance-m` is the largest AABB penetration depth
  (m) actually observed between the die's leading face and the
  blank's near face across the whole trajectory -- informational
  (this ns's tolerance check uses the force/pressure reading, not
  displacement), derived from the actual simulated positions, not
  invented.

  Pure, deterministic -- the same `press-die-mass-kg` always
  reproduces the same telemetry; no IO, no wall-clock."
  [press-die-mass-kg]
  (let [v0 press-closing-velocity-mps
        approach-m (+ gap-m die-half-w-m blank-half-w-m)
        ticks (long (+ settle-ticks (long (ceil* (/ approach-m (* v0 dt))))))
        blank-x 0.0
        die-x (- blank-x blank-half-w-m die-half-w-m gap-m)
        die (p2d/make-body {:position [die-x 0.0]
                             :velocity [v0 0.0]
                             :mass (double press-die-mass-kg)
                             :restitution 0.0
                             :friction 0.0
                             :collider (p2d/make-aabb-collider die-half-w-m die-half-h-m)
                             :user-data :press-die})
        blank (p2d/make-body {:position [blank-x 0.0]
                               :velocity [0.0 0.0]
                               :mass 0.0
                               :restitution 0.0
                               :friction 0.0
                               :collider (p2d/make-aabb-collider blank-half-w-m blank-half-h-m)
                               :user-data :sheet-metal-blank})
        w0 (p2d/world-new [0.0 0.0])
        [w1 die-id] (p2d/world-add w0 die)
        [w2 _blank-id] (p2d/world-add w1 blank)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) die-id)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (abs* (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))
        contact-plane-x (- blank-x blank-half-w-m)
        penetrations-m (mapv (fn [{:keys [position]}]
                                (max 0.0 (- (+ (first position) die-half-w-m) contact-plane-x)))
                              trajectory)
        peak-force-n (* (double press-die-mass-kg) peak-decel-mps2)]
    {:trajectory trajectory
     :sim-peak-forming-force-n peak-force-n
     :sim-peak-forming-pressure-mpa (/ peak-force-n local-die-contact-area-mm2)
     :sim-peak-draw-distance-m (reduce max 0.0 penetrations-m)
     :ticks (count trajectory)
     :dt dt
     :closing-velocity-mps v0}))

(defn press-telemetry-for
  "Runs the REAL `simulate-press` time-stepped `physics-2d` simulation
  for `body-shell`'s own recorded `:press-die-mass-kg` press-run
  configuration and returns the actual simulated telemetry:
  {:sim-peak-forming-force-n n :sim-peak-forming-pressure-mpa n
  :sim-peak-draw-distance-m n :ticks n :dt n :closing-velocity-mps n}.
  Pure, deterministic -- the same `:press-die-mass-kg` always
  reproduces the same telemetry."
  [body-shell]
  (select-keys (simulate-press (:press-die-mass-kg body-shell))
               [:sim-peak-forming-force-n :sim-peak-forming-pressure-mpa
                :sim-peak-draw-distance-m :ticks :dt :closing-velocity-mps]))

(defn forming-pressure-out-of-tolerance?
  "Ground-truth check: does `body-shell`'s own recorded REAL
  `:sim-peak-forming-pressure-mpa` (the ACTUAL `physics-2d`-simulated
  press-collision reading -- see `press-telemetry-for`) exceed the
  `forming-pressure-ceiling-mpa` for its own recorded
  `:rail-material-grade`? nil/unrecognized-grade or missing telemetry
  never fabricates a verdict -- returns false (not a HARD hold) so an
  UNRECOGNIZED grade fails safe as 'cannot compute', which
  `bodyshop.governor`'s separate spec-basis-style checks are
  responsible for catching, not this ns."
  [{:keys [sim-peak-forming-pressure-mpa rail-material-grade]}]
  (let [ceiling (forming-pressure-ceiling-mpa rail-material-grade)]
    (and (number? sim-peak-forming-pressure-mpa)
         (number? ceiling)
         (> sim-peak-forming-pressure-mpa ceiling))))

(defn simulate-stamping-press
  "Run the robot-executed stamping-press/quality-cell verification
  mission for `body-shell-id` (`body-shell` is the full record, incl.
  `:press-die-mass-kg` and `:rail-material-grade`). Actually runs the
  REAL engine: `press-telemetry-for` -- the actual `physics-2d`-stepped
  press-die/sheet-metal-blank collision trajectory
  (`:sim-peak-forming-force-n`/`:sim-peak-forming-pressure-mpa`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-peak-forming-force-n n :sim-peak-forming-pressure-mpa n}.
  Deterministic: :passed? is derived from the body-shell's OWN
  recorded press-run configuration via the REAL simulated trajectory
  (`forming-pressure-out-of-tolerance?`), never invented or randomized
  -- `kotoba.robotics` mandates no network/IO, and a repeatable
  simulation is what makes the governor's independent recheck
  (`simulation-out-of-tolerance?`) meaningful."
  [body-shell-id body-shell]
  (let [telemetry (press-telemetry-for body-shell)
        out-of-range? (forming-pressure-out-of-tolerance? (merge body-shell telemetry))
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" body-shell-id "-stamping-press")
                                   :robot/stamping-press-cell-1
                                   :stamping-press-forming-verification
                                   :boundaries {:station "body-shop-stamping-line"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :body-shell-id body-shell-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-peak-forming-force-n (:sim-peak-forming-force-n telemetry)
     :sim-peak-forming-pressure-mpa (:sim-peak-forming-pressure-mpa telemetry)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does
  `body-shell`'s OWN current, on-file real `physics-2d`-simulated
  forming-pressure telemetry (`:sim-peak-forming-pressure-mpa`) exceed
  its own rail-material-grade's forming-pressure ceiling right now?
  Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `bodyshop.registry/body-shell-dimension-out-
  of-range?`'s refusal to trust a proposal's self-report."
  [body-shell]
  (forming-pressure-out-of-tolerance? body-shell))
