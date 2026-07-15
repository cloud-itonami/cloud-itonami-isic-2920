# Business Model: Manufacture of Bodies (Coachwork) for Motor Vehicles

## Classification
- Repository: `cloud-itonami-isic-2920`
- ISIC Rev.5: `2920` — manufacture of bodies (coachwork) for motor
  vehicles — body-in-white (BIW) stamping/welding intake, material-
  certification evidence verification and Body-in-White Quality
  Certificate issuance
- Social impact: vehicle-safety, supply-resilience, industrial-jobs

## Customer
- independent body-shop plants and contract BIW stamping/welding
  shops needing auditable material-certification and production
  records
- OEM body-shop operations needing verifiable build and end-of-line
  dimensional/weld history for produced body-shells
- final-assembly-plant supplier-quality auditors needing verifiable
  material-certification evidence and conformance records before
  accepting a body-shell shipment
- programs that cannot accept closed, unauditable manufacturing-
  execution platforms

## Offer
- per-jurisdiction AHSS material-certification evidence checklist and
  jurisdiction-scope version management (SAE J2340 / JIS G 3135 /
  EN 10346)
- robotics-assisted stamping-press-forming and end-of-line
  dimensional/weld inspection records, backed by a REAL time-stepped
  `physics-2d` rigid-body forming simulation
- body-shell overall-length dimensional-deviation and weld-quality
  defect history
- Body-in-White Quality Certificate drafts and disclosure records
- role-based access and immutable audit ledger
- CSV/EDN audit package export for final-assembly-plant auditors

## Revenue
- self-host setup fee
- managed hosting subscription per plant / stamping line
- support retainer with SLA
- stamping-press/weld-cell robot integration and maintenance

## Trust Controls
- out-of-spec body-shells are blocked; a Body-in-White Quality
  Certificate is mandatory for shipment paths; body-shell history is
  immutable
- a robot action the governor refuses is never dispatched to hardware
- every shipment, hold, approval and disclosure path is auditable
- sensitive design and production data stays outside Git
- a fabricated material-cert-rules citation, incomplete evidence, an
  out-of-spec body-shell dimension, a robotics simulation that never
  ran or independently disagrees, or an unresolved weld-quality
  defect -- each forces a hold, not an override
- Body-in-White Quality Certificate issuance is logged and escalated,
  and cannot be finalized twice for the same body-shell
