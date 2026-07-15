# cloud-itonami-isic-2920

Open Business Blueprint for **ISIC Rev.5 2920**: manufacture of
bodies (coachwork) for motor vehicles -- body-in-white (BIW)
stamping/welding intake, per-jurisdiction material-certification
evidence verification, end-of-line dimensional/weld-quality
screening, robot stamping-press-forming simulation and Body-in-White
Quality Certificate finalization for a community body-shop plant.

This repository publishes a body-shop actor -- body-shell intake,
per-jurisdiction advanced-high-strength-steel (AHSS) material-
certification evidence-checklist verification, end-of-line
dimensional/weld-quality defect screening, robot stamping-press-
forming mission and Body-in-White Quality Certificate issuance -- as
an OSS business that any qualified body-shop plant can fork, deploy,
run, improve and sell, so a plant keeps its own production and
quality-conformance history instead of renting a closed MES / quality
SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Body Shop Advisor ⊣
Stamping Governor**.

## Scope note: body-in-white stamping/welding, upstream of final assembly

This repository is scoped to **producing a welded body-in-white (BIW)
shell** -- stamping coiled sheet steel into panels and welding them
into a body structure -- the stage that happens **before** a body
shell goes to paint/powertrain/trim installation. It is not the
final-assembly, parts-supplier or raw-material vertical. Distinct
from:

- `cloud-itonami-isic-2910` -- manufacture of motor vehicles (OEM
  final assembly: paint, powertrain/trim installation, vehicle
  type-approval/homologation, Certificate of Conformity). ISIC 2920
  sits DIRECTLY UPSTREAM of 2910 in the automotive value chain -- a
  body shop ships a welded body-in-white shell to the final-assembly
  plant that isic-2910 models, which is the customer relationship
  this actor's `:actuation/ship-body-shell` models (see `Upstream ->
  downstream hand-off` below).
- `cloud-itonami-isic-2930` -- manufacture of parts and accessories
  for motor vehicles (a Tier-1/Tier-2 supplier producing brake pads,
  wiring harnesses, seats, fasteners, etc. for shipment to an OEM).
  ISIC 2920 is the OEM's OWN body-shop stamping/welding operation
  (or a dedicated BIW contract shop), not a Tier-1/2 component
  supplier -- a body shop consumes coiled sheet steel and (per
  `cloud-itonami-isic-2410`'s own README) already-implemented basic
  iron/steel output, not a Tier-1/2 part-lot.
- `cloud-itonami-isic-2410` -- manufacture of basic iron and steel
  (raw material). ISIC 2410's steel-mill output (coiled sheet steel
  in real AHSS grades) is the RAW-MATERIAL input this actor's
  stamping press consumes; ISIC 2920 is one stage downstream of 2410
  in the same value chain.

## Upstream -> downstream hand-off (2410 -> 2920 -> 2910)

```text
cloud-itonami-isic-2410 (basic iron/steel, coiled AHSS sheet)
  --> cloud-itonami-isic-2920 (THIS repo: stamping + welding -> welded body-in-white shell)
  --> cloud-itonami-isic-2910 (final assembly: paint/powertrain/trim, vehicle type-approval)
```

`:actuation/ship-body-shell` is the REAL hand-off event: a body shop
dispatches a finished, certified body-in-white shell onward to the
final-assembly plant, which is `cloud-itonami-isic-2910`'s own
`:vehicle/intake` on the receiving end. This actor's `:rail-material-
grade` vocabulary (`:DP600`/`:DP980`/`:boron-PHS`) deliberately reuses
the SAME real AHSS grade names `kami-engine-vehicle-designer`'s
`vdesign.simverify` geometry map already cites for automotive's own
crash-structural model -- a body-shell's rail material grade IS the
literal input to isic-2910's downstream crash-safety check, so
keeping the same real grade vocabulary across both actors is a
genuine, valuable cross-actor consistency, not just cosmetic (see
`bodyshop.robotics`'s own docstring for the exact published
yield-strength anchors each grade uses).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (stamping-press
forming, weld-cell operation, end-of-line dimensional/weld scan)
operate under an actor that proposes actions and an independent
**Stamping Governor** that gates them. The governor never issues a
Body-in-White Quality Certificate itself; `:high`/`:safety-critical`
actions (`:actuation/ship-body-shell`, `:actuation/issue-body-
certificate`) require human sign-off.

**Robot process simulation is a REAL, time-stepped physics
simulation, not a symbolic field comparison** (native from day one,
per ADR-2607151600/ADR-2607152000's fleet pattern -- this vertical is
a NEW actor built to that standard, not a retrofit): `bodyshop.
robotics` walks every body-shell through a robot-executed stamping-
press-forming verification mission (`kotoba.robotics` mission/action/
telemetry-proof contracts) -- a real, tested rigid-body physics engine
(`kotoba-lang/physics-2d`) time-steps a press-die rigid body closing
at a controlled velocity onto a static sheet-metal-blank rigid body,
and reads a real peak forming force/pressure (`:sim-peak-forming-
force-n`/`:sim-peak-forming-pressure-mpa`, Newtons/MPa) directly off
the simulated collision -- not an invented or hand-set number. The
Stamping Governor independently re-derives the body-shell's own
`:sim-peak-forming-pressure-mpa` against a real, disclosed ceiling
derived from its own rail-material-grade's published yield strength
(`bodyshop.robotics/forming-pressure-ceiling-mpa`), never trusting the
mission's self-reported verdict alone (see `bodyshop.robotics`'s own
docstring for the full honest disclosure of every engineering prior
this simulation uses).

## Core contract

```text
body-shell intake + material-cert-rules verify + end-of-line quality screen
  -> Body Shop Advisor proposal
  -> Stamping Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Shipping a body-shell onward to final assembly via a robot handling/
dispatch action and issuing a Body-in-White Quality Certificate
produce **unsigned draft records and ledger facts only**. This actor
does not talk to real plant control systems or a final-assembly
plant's own intake portal. Signature and hardware dispatch are the
body-shop plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:body-shell/intake` | normalize body-shell directory patch (phase 3 may auto-commit when clean) |
| `:material-cert-rules/verify` | per-jurisdiction AHSS material-certification evidence checklist (always human) |
| `:end-of-line-quality/screen` | end-of-line dimensional/weld-quality defect screen (HARD hold if unresolved) |
| `:robotics/simulate-stamping-press` | robot stamping-press-forming verification mission (always human; required on file before shipment) |
| `:actuation/ship-body-shell` | draft body-shell-shipment record onward to final assembly (always human; HARD hold if robotics-sim missing, independently out-of-tolerance, or dimension out of range) |
| `:actuation/issue-body-certificate` | draft Body-in-White Quality Certificate record (always human) |

## Social / regulatory hand-off

```clojure
(require '[bodyshop.store :as store]
         '[bodyshop.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for final-assembly/quality-audit hand-off
(export/package->csv-bundle db)     ;; CSV bundle (body-shells/ledger/shipments/body-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2920/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2920
```

Writes CSV files under `out/audit-package/` (or the given directory).
