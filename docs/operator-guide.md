# Operator Guide

## First Deployment
1. Register quality engineers, plants, body-shells, personnel and
   robots.
2. Import historical body-shell / weld-quality / material-cert
   records.
3. Run read-only validation and robot mission dry-runs.
4. Configure material-cert evidence checklists and human sign-off
   paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before dispatch
- human sign-off for `:high`/`:safety-critical` robot actions (e.g.
  stamping-press-forming on structural body-shells, Body-in-White
  Quality Certificate issuance)
- audit export for every shipment, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for safety-affecting actions.

## Operating states
intake : material-cert-rules-verify : end-of-line-quality-screen : approve : ship-body-shell : issue-body-certificate : audit

## Audit export (social operation)

After a production session, export the append-only package for
final-assembly-plant quality auditors or internal compliance:

```clojure
(require '[bodyshop.store :as store]
         '[bodyshop.export :as export])
(export/audit-package store)        ; EDN maps
(export/package->csv-bundle store)  ; CSV files as string map
```

Drafts remain **unsigned** — signing and shipment to the final-
assembly plant's own intake are the body-shop plant's own acts (see
README Actuation honesty).

Static UI sample: `docs/samples/operator-console.html`.
