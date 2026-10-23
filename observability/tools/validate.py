#!/usr/bin/env python3
"""Static checks on the generated dashboards (JSON, datasource uids, ids, gridPos overlap, PromQL bracket/quote balance)."""
import json, glob, os, re, shutil, subprocess, sys
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "grafana", "dashboards")
OK = {"mimir", "loki", "tempo", "-- Grafana --"}
bad = 0
def balanced(e):
    st = []; q = None
    for c in e:
        if q:
            if c == q: q = None
            continue
        if c in "\"'": q = c
        elif c in "([{": st.append(c)
        elif c in ")]}":
            if not st or "([{".index(st.pop()) != ")]}".index(c): return False
    return not st and q is None
for f in sorted(glob.glob(D + "/*.json")):
    d = json.load(open(f)); ps = d["panels"]; ids = [p["id"] for p in ps]
    errs = []
    if len(ids) != len(set(ids)): errs.append("duplicate ids")
    boxes = []
    for p in ps:
        g = p["gridPos"]
        for o in boxes:
            if g["x"] < o["x"] + o["w"] and o["x"] < g["x"] + g["w"] and g["y"] < o["y"] + o["h"] and o["y"] < g["y"] + g["h"]: errs.append("overlap id %s" % p["id"])
        boxes.append(g)
        if g["x"] + g["w"] > 24: errs.append("too wide %s" % p["id"])
        if p["type"] == "row": continue
        if p["type"] != "text" and p.get("datasource", {}).get("uid") not in OK: errs.append("ds %s" % p["id"])
        for t in p.get("targets", []):
            if t["datasource"]["uid"] not in OK: errs.append("target ds %s" % p["id"])
            e = t.get("expr")
            if e is not None and (not e.strip() or not balanced(e)): errs.append("promql/logql unbalanced in '%s': %s" % (p["title"], e))
            if t["datasource"]["uid"] == "mimir" and shutil.which("promtool") and e:
                pass
    print(os.path.basename(f), d["uid"], "OK" if not errs else errs); bad += bool(errs)
sys.exit(bad)
