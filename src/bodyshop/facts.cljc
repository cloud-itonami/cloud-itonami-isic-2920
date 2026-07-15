(ns bodyshop.facts
  "Per-jurisdiction advanced-high-strength-steel (AHSS) material-
  certification evidence catalog -- the G2-style spec-basis table the
  Stamping Governor checks every `:material-cert-rules/verify`
  proposal against.

  Like `cloud-itonami-isic-2930`'s PPAP catalog (an OEM-customer-
  driven INDUSTRY quality-management requirement) and UNLIKE
  `cloud-itonami-isic-2910`'s vehicle type-approval (a GOVERNMENT-
  mandated statute), the standards cited below are voluntary
  INDUSTRY/technical-standards-body material specifications that OEM
  purchase contracts require a body-shell's rail-material mill
  certification to conform to -- SAE, JISC and CEN publish and
  steward these standards, they do not enact vehicle-safety law. This
  catalog cites each authority and standard honestly for what it
  actually is -- never inflates an industry material spec into a
  statute, never invents one.

  Real anchors (also cited in the README's Scope note and
  `bodyshop.robotics`'s own docstring for the panel-yield-strength
  table):
    - USA:      SAE J2340 -- \"Categorization and Properties of Dent
                Resistant, High Strength, and Ultra-High Strength
                Automotive Sheet Steel\" (SAE International).
    - Japan:    JIS G 3135 -- \"Cold-reduced high strength steel sheet
                and strip with improved formability for automobile\"
                (JISC -- Japanese Industrial Standards Committee).
    - Germany/
      EU:       EN 10346 -- \"Continuously hot-dip coated steel flat
                products for cold forming -- Technical delivery
                conditions\" (CEN -- European Committee for
                Standardization).

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official material-standard bodies;
  this is a starting catalog, not a survey of every market or every
  OEM's own supplement.")

(def catalog
  {"USA" {:name "United States"
          :owner-authority "SAE International"
          :legal-basis "SAE J2340 -- Categorization and Properties of Dent Resistant, High Strength, and Ultra-High Strength Automotive Sheet Steel (industry material-classification standard, not a government statute)"
          :national-spec "SAE J2340 dual-phase/martensitic/press-hardened steel grade designations for automotive body sheet"
          :provenance "https://www.sae.org/standards/content/j2340_200502/"
          :required-evidence ["Mill test report (grade + mechanical properties per SAE J2340)"
                              "Chemical composition certificate"
                              "Coating designation certificate (for coated grades)"
                              "Formability test report (n-value/r-value)"]}
   "DEU" {:name "Germany"
          :owner-authority "CEN (European Committee for Standardization) / DIN"
          :legal-basis "EN 10346 -- Kontinuierlich schmelztauchveredeltes Band und Blech aus Stahl -- Technische Lieferbedingungen (harmonisierte Industrienorm; kein Gesetz)"
          :national-spec "EN 10346 Werkstoffzeugnis fuer schmelztauchveredelte Karosserieblech-Coils"
          :provenance "https://www.din.de/en/getting-involved/standards-committees/nar/standards/wdc-beuth:din21:315330679"
          :required-evidence ["Werkstoffzeugnis / mill test report (grade + mechanical properties per EN 10346)"
                              "Chemische Zusammensetzung (chemical composition certificate)"
                              "Beschichtungszeugnis (coating designation certificate, for coated grades)"
                              "Umformbarkeitspruefbericht (formability test report)"]}
   "JPN" {:name "Japan"
          :owner-authority "日本産業標準調査会 (JISC -- Japanese Industrial Standards Committee)"
          :legal-basis "JIS G 3135 自動車用加工性熱延鋼板及び鋼帯 / 自動車用高強度冷延鋼板 (産業標準。法定の車両安全基準ではなく購買契約上の材料規格)"
          :national-spec "JIS G 3135 準拠のミルシート(材料証明書)"
          :provenance "https://www.jisc.go.jp/"
          :required-evidence ["ミルシート/材料証明書 (mill test report per JIS G 3135)"
                              "化学成分証明書 (chemical composition certificate)"
                              "めっき仕様証明書 (coating designation certificate, coated grades)"
                              "成形性試験報告書 (formability test report)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2920 R0: " (count catalog)
                 " jurisdictions seeded. Extend `bodyshop.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
