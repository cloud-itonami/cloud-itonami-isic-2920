(ns bodyshop.bodyshopadvisor
  "Body Shop Advisor client -- the *contained intelligence node* for
  the body-in-white (BIW) stamping/welding body-shop actor.

  It normalizes body-shell intake, drafts a per-jurisdiction material-
  certification evidence checklist, screens body-shells for an
  unresolved end-of-line weld-quality defect, drafts the body-shell-
  shipment action (onward to final assembly), and drafts the Body-in-
  White Quality Certificate issuance action. CRITICAL: it is a
  smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a
  real robot shipment/certificate issuance. Every output is censored
  downstream by `bodyshop.governor` before anything touches the SSoT,
  and `:actuation/ship-body-shell`/`:actuation/issue-body-certificate`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/ship-body-shell | :actuation/issue-body-certificate | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [bodyshop.facts :as facts]
            [bodyshop.registry :as registry]
            [bodyshop.robotics :as robotics]
            [bodyshop.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the body-shell, dimensional figures or jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "ボディシェル記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :body-shell/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-requirements
  "Per-jurisdiction material-certification evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `bodyshop.facts` -- the Stamping Governor must reject this (never
  invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/body-shell db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "bodyshop.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :material-cert-verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要材料証明書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 根拠規格: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :material-cert-verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-weld-quality
  "End-of-line weld/dimensional-quality defect screening draft.
  `:weld-quality-defect-unresolved?` on the body-shell record injects
  the failure mode: the Stamping Governor must HOLD, un-overridably,
  on any unresolved defect."
  [db {:keys [subject]}]
  (let [a (store/body-shell db subject)]
    (cond
      (nil? a)
      {:summary "対象ボディシェル記録が見つかりません" :rationale "no body-shell record"
       :cites [] :effect :weld-quality-screen/set :value {:body-shell-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:weld-quality-defect-unresolved? a))
      {:summary    (str (:shell-name a) ": 未解決の溶接/寸法品質欠陥を検出")
       :rationale  "エンドオブライン品質スクリーニングが未解決の欠陥を検出。人手確認とホールドが必須。"
       :cites      [:end-of-line-quality-check]
       :effect     :weld-quality-screen/set
       :value      {:body-shell-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:shell-name a) ": 未解決の溶接/寸法品質欠陥なし")
       :rationale  "エンドオブライン品質欠陥スクリーニング完了。"
       :cites      [:end-of-line-quality-check]
       :effect     :weld-quality-screen/set
       :value      {:body-shell-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- simulate-stamping-press
  "Runs the robot stamping-press-forming verification mission
  (`bodyshop.robotics`) and drafts its result as a proposal. High
  confidence -- the mission itself is REAL `physics-2d`-simulated
  press-die/sheet-metal-blank forming telemetry derived from the
  body-shell's own recorded `:press-die-mass-kg` (ADR-2607152000), not
  an LLM guess; the Stamping Governor still independently re-derives
  :passed? from that same telemetry before any `:actuation/ship-body-
  shell` proposal may commit -- see `bodyshop.governor`'s `robotics-
  simulation-violations`."
  [db {:keys [subject]}]
  (let [a (store/body-shell db subject)]
    (if (nil? a)
      {:summary "対象ボディシェル記録が見つかりません" :rationale "no body-shell record"
       :cites [] :effect :body-shell/upsert :value {:id subject :robotics-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed?]} (robotics/simulate-stamping-press subject a)]
        {:summary    (str subject ": スタンピングプレス成形ロボット検証ミッション " (if passed? "合格" "不合格"))
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " sim-peak-forming-pressure-mpa=" (:sim-peak-forming-pressure-mpa a))
         :cites      [(:mission/id mission)]
         :effect     :body-shell/upsert
         :value      {:id subject
                      :robotics-sim-verified? passed?
                      :robotics-sim-record {:mission-id (:mission/id mission)
                                            :actions (mapv #(dissoc % :action) actions)
                                            :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- propose-body-shell-shipment
  "Draft the actual BODY-SHELL-SHIPMENT action -- dispatching a real
  robot shipment/handling action releasing a welded body-in-white
  shell onward to the final-assembly plant. ALWAYS `:stake :actuation/
  ship-body-shell` -- this is a REAL-WORLD safety-critical act, never
  a draft the actor may auto-run. See README `Actuation`: no phase
  ever adds this op to a phase's `:auto` set (`bodyshop.phase`); the
  governor also always escalates on `:actuation/ship-body-shell`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/body-shell db subject)]
    {:summary    (str subject " 向けボディシェル出荷提案"
                      (when a (str " (body-shell=" (:shell-name a) ")")))
     :rationale  (if a
                   (str "overall-length-actual-mm=" (:overall-length-actual-mm a)
                        " spec=[" (:overall-length-min-mm a) "," (:overall-length-max-mm a) "]")
                   "ボディシェル記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :body-shell/mark-shipped
     :value      {:body-shell-id subject}
     :stake      :actuation/ship-body-shell
     :confidence (if (and a (not (registry/body-shell-dimension-out-of-range? a))) 0.9 0.3)}))

(defn- propose-body-certificate
  "Draft the actual BODY-IN-WHITE QUALITY CERTIFICATE action --
  issuing a real body-shop QA certificate certifying a body-shell's
  dimensional/weld/material conformance before onward shipment.
  ALWAYS `:stake :actuation/issue-body-certificate` -- this is a
  REAL-WORLD safety-critical act, never a draft the actor may
  auto-run. See README `Actuation`: no phase ever adds this op to a
  phase's `:auto` set (`bodyshop.phase`); the governor also always
  escalates on `:actuation/issue-body-certificate`. Two independent
  layers agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/body-shell db subject)]
    {:summary    (str subject " 向けボディインホワイト品質証明書発行提案"
                      (when a (str " (body-shell=" (:shell-name a) ")")))
     :rationale  (if a
                   "jurisdiction-evidence-checklist referenced"
                   "ボディシェル記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :body-shell/mark-certified
     :value      {:body-shell-id subject}
     :stake      :actuation/issue-body-certificate
     :confidence (if a 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :body-shell/intake                          (normalize-intake db request)
    :material-cert-rules/verify                 (verify-requirements db request)
    :end-of-line-quality/screen                 (screen-weld-quality db request)
    :robotics/simulate-stamping-press           (simulate-stamping-press db request)
    :actuation/ship-body-shell                  (propose-body-shell-shipment db request)
    :actuation/issue-body-certificate           (propose-body-certificate db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはボディインホワイト(BIW)スタンピング/溶接ボディショップ工場の"
       "出荷実行・品質証明書発行エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:body-shell/upsert|:material-cert-verification/set|:weld-quality-screen/set|"
       ":body-shell/mark-shipped|:body-shell/mark-certified) "
       "(:robotics/simulate-stamping-press も :body-shell/upsert で "
       ":robotics-sim-verified? を提案する) "
       ":stake(:actuation/ship-body-shell か :actuation/issue-body-certificate か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :material-cert-rules/verify                 {:body-shell (store/body-shell st subject)}
    :end-of-line-quality/screen                 {:body-shell (store/body-shell st subject)}
    :robotics/simulate-stamping-press            {:body-shell (store/body-shell st subject)}
    :actuation/ship-body-shell                   {:body-shell (store/body-shell st subject)}
    :actuation/issue-body-certificate             {:body-shell (store/body-shell st subject)}
    {:body-shell (store/body-shell st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Stamping Governor
  escalates/holds -- an LLM hiccup can never auto-ship a body-shell
  action or auto-issue a body certificate."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :bodyshopadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
