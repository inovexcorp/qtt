#!/usr/bin/env python3
"""
Smoke test a running QTT (scripts/stack.sh up) against a real Anzo server. Read-only queries only.

    ANZO_URL=https://anzo.example.com ANZO_USER=... ANZO_PASS=... python3 scripts/smoke_real_anzo.py

Optional: ANZO_GRAPHMART (default: the first active graphmart whose ontology queries succeed is not
searched for; pass one explicitly to make results comparable between runs).
Credentials are read from the environment only and are never printed.
"""
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

API = os.environ.get("QTT_API", "https://localhost:18443/queryrest/api")
QUERY = os.environ.get("QTT_QUERY", "http://localhost:18082/query")
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
GRAPHMART = os.environ.get("ANZO_GRAPHMART", "http://cambridgesemantics.com/Graphmart/93794a1ba2a24246a0f33c4340858d80")
TEMPLATE = """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
CONSTRUCT { ?s a ?t ; rdfs:label ?l }
WHERE { ?s a ?t ; rdfs:label ?l . <#if headers.label??>FILTER(CONTAINS(LCASE(STR(?l)), "${headers.label}"))</#if> }
LIMIT 10"""

results = []


def http(method, url, body=None, headers=None, timeout=90):
    headers = dict({"Accept": "application/json"}, **(headers or {}))
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
            return r.status, r.read(), time.time() - start
    except urllib.error.HTTPError as e:
        return e.code, e.read(), time.time() - start


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name} {detail}")


def main():
    url, user, pw = os.environ["ANZO_URL"], os.environ["ANZO_USER"], os.environ["ANZO_PASS"]
    suffix = uuid.uuid4().hex[:6]
    ds_id, route = f"smoke-ds-{suffix}", f"smoke-route-{suffix}"
    ds = {"dataSourceId": ds_id, "timeOutSeconds": "60", "maxQueryHeaderLength": "8192", "username": user,
          "password": pw, "url": url, "validateCertificate": True}
    try:
        s, b, t = http("POST", API + "/datasources/test", json.dumps(ds).encode(), {"Content-Type": "application/json"})
        check("connection test", s == 200 and json.loads(b).get("status") == "success", f"({t:.1f}s)")
        s, _, _ = http("POST", API + "/datasources", json.dumps(ds).encode(), {"Content-Type": "application/json"})
        check("create datasource", s == 201)
        s, b, t = http("POST", f"{API}/datasources/{ds_id}/healthcheck")
        check("health check UP", s == 200 and json.loads(b).get("status") == "UP", f"({t:.1f}s)")
        s, b, t = http("GET", f"{API}/anzo/graphmarts/{ds_id}")
        gms = json.loads(b) if s == 200 else []
        check("graphmart lookup", s == 200 and any(g["iri"] == GRAPHMART for g in gms), f"({len(gms)} graphmarts, {t:.1f}s)")
        s, b, t = http("GET", f"{API}/anzo/layers/{ds_id}?graphmartUri={urllib.parse.quote(GRAPHMART, safe='')}")
        layers = json.loads(b) if s == 200 else []
        check("layer lookup", s == 200 and len(layers) > 0, f"({len(layers)} layers, {t:.1f}s)")
        layer_csv = ",".join(l["iri"] for l in layers)

        boundary = "----smoke"
        parts = "".join(
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"{n}\"; filename=\"blob\"\r\n"
            f"Content-Type: multipart/form-data\r\n\r\n{v}\r\n" for n, v in (("freemarker", TEMPLATE), ("layers", layer_csv)))
        q = urllib.parse.urlencode({"routeParams": "httpMethodRestrict=GET,POST", "dataSourceId": ds_id,
                                    "description": "smoke", "graphMartUri": GRAPHMART})
        s, b, _ = http("POST", f"{API}/routes/{route}?{q}", (parts + f"--{boundary}--\r\n").encode(),
                       {"Content-Type": f"multipart/form-data; boundary={boundary}"})
        check("create route", s == 201)
        s, b, t = http("GET", f"{QUERY}/{route}")
        body = b.decode(errors="replace")
        check("query route end to end", s == 200 and "@id" in body, f"(HTTP {s}, {len(body)} bytes, {t:.1f}s)")
        s, b, t = http("GET", f"{QUERY}/{route}?label=a")
        check("query route with parameter", s == 200, f"(HTTP {s}, {t:.1f}s)")
        s, b, t = http("GET", f"{API}/ontology/{route}?limit=1000")
        n = len(json.loads(b)) if s == 200 else 0
        check("ontology load", s == 200 and n > 0, f"({n} elements, {t:.1f}s)")
    finally:
        http("DELETE", f"{API}/routes/{route}")
        http("DELETE", f"{API}/datasources/{ds_id}")
    failed = [r for r in results if not r[1]]
    print(f"real-Anzo smoke: {len(results) - len(failed)}/{len(results)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
