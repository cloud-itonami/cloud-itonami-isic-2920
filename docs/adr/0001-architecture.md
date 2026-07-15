# ADR-0001: Body Shop Advisor ⊣ Stamping Governor architecture

- Status: Accepted (2026-07-16)
- Repository: `cloud-itonami-isic-2920` (ISIC Rev.5 `2920`)

## Context

Body-in-white (BIW) stamping/welding body-shop manufacturing (turning
coiled sheet steel into a welded body structure, end-of-line
dimensional/weld-quality inspection, material-certification evidence
verification, Body-in-White Quality Certificate issuance) needs the
same governed-actor pattern as the rest of the cloud-itonami fleet: an
untrusted advisor proposes; an independent governor may HOLD;
high-stakes actuation never auto-commits.

The industry-registry entry for `2920` had sat at `:maturity :spec`
placeholder (`gftdcojp/cloud-itonami-C2920`) with no repo, no business
model, no actor. A 2026-07-16 value-chain review found `cloud-itonami-
isic-2410` (basic iron/steel, raw material), `cloud-itonami-isic-2910`
(motor-vehicle final assembly) and `cloud-itonami-isic-2930` (auto
parts/accessories, a Tier-1/2 supplier concern) all implemented, but
the body-in-white stamping/welding stage directly upstream of final
assembly -- the stage that actually turns raw steel into a vehicle
body shell -- had no actor at all: a real gap in the middle of the
automotive value chain, distinct from all three neighboring
verticals (see README `Scope note`).

This vertical additionally adopts ADR-2607151600/ADR-2607152000's
real-engineering-simulation fleet pattern NATIVELY from day one
(unlike the 6 verticals ADR-2607152000 itself upgraded from a prior
symbolic layer) -- mirroring how `cloud-itonami-isic-2930`/`cloud-
itonami-isic-2394` were built real-physics-first.

## Decision

1. Namespaces live under `bodyshop.*` with the standard
   facts / registry / store / governor / phase / advisor / operation / sim /
   robotics / export shape.
2. Entity is a **body-shell** (a produced body-in-white unit, tracked
   per body-serial), not a finished vehicle, a part-lot, or a steel
   heat.
3. Dual actuation on the same entity:
   - `:actuation/ship-body-shell` (robot body-shell-shipment dispatch
     draft, onward to the final-assembly plant -- the real upstream
     hand-off to `cloud-itonami-isic-2910`'s own `:vehicle/intake`)
   - `:actuation/issue-body-certificate` (Body-in-White Quality
     Certificate draft)
4. Double-actuation guards use dedicated booleans
   (`:body-shell-shipped?`, `:body-certified?`), never a status
   lifecycle (ADR-2607071320 / 6492 lesson).
5. `body-shell-dimension-out-of-range?` continues the fleet
   two-sided range check family (after testlab / conservation / water
   / steelworks / turbine / automotive / autoparts), applied here to
   a body-shell's own measured overall-length dimensional deviation
   against its own recorded nominal-body-dimension spec bounds.
6. `bodyshop.robotics` delivers a REAL, time-stepped `physics-2d`
   rigid-body stamping-press-forming simulation from day one (not a
   symbolic field comparison, and not a retrofit): a press-die
   `Body2D` closes at a controlled velocity onto a static sheet-
   metal-blank `Body2D`; `:sim-peak-forming-force-n`/`:sim-peak-
   forming-pressure-mpa` are read directly off the actual simulated
   collision trajectory. The governor HARD-holds if the mission never
   ran, OR if an independent recompute of the body-shell's own
   `:sim-peak-forming-pressure-mpa` exceeds a real, disclosed ceiling
   (`forming-pressure-ceiling-multiple` x the rail-material-grade's
   published yield strength) -- never trusting the mission's
   self-reported verdict.
7. Rail-material-grade vocabulary (`:DP600`/`:DP980`/`:boron-PHS`)
   deliberately reuses the SAME real AHSS grades `kami-engine-vehicle-
   designer`'s `vdesign.simverify` geometry map already cites for
   automotive's own crash structural model -- genuine cross-actor
   consistency across the 2920 -> 2910 hand-off, not cosmetic.
8. Weld-quality defect unresolved is evaluated unconditionally so
   `:end-of-line-quality/screen` itself can HARD-hold (parksafety
   ADR-2607071922 Decision 5 discipline, same as `automotive.
   governor`'s end-of-line-defect-unresolved check / `autoparts.
   governor`'s process-capability-defect-unresolved check).
9. Material-cert evidence catalog seeds USA (SAE J2340) / DEU
   (EN 10346) / JPN (JIS G 3135) only; missing jurisdictions are
   uncovered, never fabricated. These are voluntary INDUSTRY material
   standards an OEM purchase contract requires, not a government
   vehicle-safety statute (the same honest distinction `autoparts.
   facts` draws for PPAP vs. `automotive.facts`'s government
   type-approval statutes).

## Consequences

(+) The body-in-white stamping/welding stage gains a forkable OSS
operating stack with auditable governor holds, closing the mid-
supply-chain gap the 2026-07-16 value-chain review identified.
(+) Delivers a REAL time-stepped physics simulation (not a symbolic
comparison) as a native part of this actor's initial build, extending
ADR-2607151600/ADR-2607152000's fleet pattern to a NEW actor rather
than retrofitting an existing symbolic one.
(+) Genuine cross-actor material-grade-vocabulary consistency with
`cloud-itonami-isic-2910`'s downstream crash-structural check.
(−) No physical plant digital-twin tick beyond the single stamping-
press-forming physics check in this repo (follow-up domain data is
out of scope here).
(−) Material-cert-authority coverage is a starting catalog (3
jurisdictions), not exhaustive, and does not capture OEM-specific
material supplements.
(−) `physics-2d` is a 2D projection with no material-stiffness/
stress-strain model -- the forming-pressure-ceiling check is an honest
engineering proxy for a forming-limit-diagram exceedance, not a
literal transcription of one specific named tear-threshold standard
(see `bodyshop.robotics`'s own docstring for the full disclosure).

## Related

- ADR-2607011000 (robotics premise + ISIC coverage)
- ADR-2607111600 (isic-2910 motor-vehicle promotion -- sibling
  architecture this repo mirrors)
- ADR-2607151600 (real engineering-simulation integration, automotive
  pilot)
- ADR-2607152000 (real engineering-simulation fleet extension)
- Superproject fleet ADR for this promotion: `90-docs/adr/2607160200-
  cloud-itonami-isic-2920-bodyshop.md`
- Sibling architecture: `cloud-itonami-isic-2930` docs/adr/0001,
  `cloud-itonami-isic-2394` docs/adr/0001
