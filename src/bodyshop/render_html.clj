(ns bodyshop.render-html
  "Build-time HTML renderer. Drives the REAL actor stack deterministically."
  (:require [clojure.string :as str]
            [bodyshop.store :as store]
            [bodyshop.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator {:actor-id "op-1" :actor-role :quality-engineer :phase 3})
(defn- exec! [actor tid request] (g/run* actor {:request request :context operator} {:thread-id tid}))
(defn- approve! [actor tid] (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn run-demo! []
  (let [db (store/seed-db) actor (op/build db)]
    (exec! actor "t1" {:op :body-shell/intake :subject "shell-1" :effect :propose
                       :patch {:id "shell-1" :material "HSLA-950"}})
    (exec! actor "t2" {:op :material-cert-rules/verify :subject "shell-1" :effect :propose})
    (approve! actor "t2")
    (exec! actor "t3" {:op :end-of-line-quality/screen :subject "shell-1" :effect :propose})
    (approve! actor "t3")
    (exec! actor "t4" {:op :actuation/ship-body-shell :subject "shell-1" :effect :propose})
    (approve! actor "t4")
    (exec! actor "t5" {:op :body-shell/intake :subject "shell-999" :effect :propose
                       :patch {:id "shell-999" :material "Unknown"}})
    db))

(defn- esc [v] (-> (str v) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- last-fact-for [ledger sid] (last (filter #(= (:subject %) sid) ledger)))
(defn- status-cell [ledger sid]
  (let [f (last-fact-for ledger sid)]
    (cond (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved</span>"
      (= :governor-hold (:t f)) (let [rule (-> f :basis first)] (str "<span class=\"critical\">HARD hold: " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))
(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))
(def ^:private gate-rows
  ["        <tr><td><code>:body-shell/intake</code></td><td><span class=\"ok\">auto-commit when clean</span></td></tr>"
   "        <tr><td><code>:material-cert-rules/verify</code></td><td><span class=\"warn\">ALWAYS human approval; cert evidence required before shipping</span></td></tr>"
   "        <tr><td><code>:end-of-line-quality/screen</code></td><td><span class=\"warn\">ALWAYS human approval</span></td></tr>"
   "        <tr><td><code>:robotics/simulate-stamping-press</code></td><td><span class=\"warn\">ALWAYS human approval (press forming test)</span></td></tr>"
   "        <tr><td><code>:actuation/ship-body-shell</code></td><td><span class=\"warn\">ALWAYS human approval; material-cert prerequisite</span></td></tr>"
   "        <tr><td><code>:actuation/issue-stamping-certificate</code></td><td><span class=\"warn\">ALWAYS human approval (actuation)</span></td></tr>"])
(defn render [db]
  (let [ledger (vec (store/ledger db))
        shells (->> (store/all-body-shells db) (sort-by :id))
        srow (fn [s] (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>" (esc (:id s)) (esc (or (:material s) "-")) (status-cell ledger (:id s))))
        srows (str/join "\n" (map srow shells))
        lrows (str/join "\n" (map ledger-row ledger))]
    (str "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-2920</title>"
     "<style>body{font:14px/1.5 sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#2a0a0a;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".muted{color:#777;font-size:.82rem}table{border-collapse:collapse;width:100%;font-size:.85rem}"
     "th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}"
     "code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}</style></head><body>"
     "<header class=\"bar\"><h1>Auto body manufacturing ops (ISIC 2920) — <code>bodyshop</code></h1></header><main>"
     "<section class=\"card\"><h2>Production body shells</h2>"
     "<p class=\"muted\">Demo from <code>bodyshop.store</code> via <code>bodyshop.render-html</code>. No invented data.</p>"
     "<table><thead><tr><th>Shell</th><th>Material</th><th>Last op</th></tr></thead><tbody>" srows "</tbody></table></section>"
     "<section class=\"card\"><h2>Action gate</h2>"
     "<table><thead><tr><th>Op</th><th>Gate</th></tr></thead><tbody>" (str/join "\n" gate-rows) "</tbody></table></section>"
     "<section class=\"card\"><h2>Audit ledger</h2>"
     "<table><thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead><tbody>" lrows "</tbody></table></section>"
     "</main></body></html>")))
(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!) f (java.io.File. out)]
    (.. f getParentFile mkdirs) (spit f (render db))
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
