#!/usr/bin/env python3
"""
Measure a QTT image: size, cold start, memory, and latency/throughput under fixed load.

    python3 scripts/measure.py --image inovexis/qtt:latest --label "phase0-karaf-baseline"

Conditions are fixed so numbers are comparable between phases:
  * podman, QTT container limited to 2 CPUs / 2 GB
  * PostgreSQL in its own container, so every cold-start run is a brand-new QTT container
    (no bundle cache, no JIT state) while the 25 seeded routes persist in the database
  * mock Anzo (WireMock) with recorded responses and no artificial delay
Results are appended to docs/perf/measurements.md and saved as JSON in docs/perf/results/.
"""
import argparse
import datetime
import gzip
import json
import os
import re
import ssl
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENGINE = os.environ.get("ENGINE", "podman")
STACK = os.path.join(ROOT, "scripts", "stack.sh")
API = "https://localhost:18443/queryrest/api"
QUERY = "http://localhost:18888"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
SEED_ROUTES = 25
GRAPHMART = "http://cambridgesemantics.com/Graphmart/93794a1ba2a24246a0f33c4340858d80"
LAYER = "http://cambridgesemantics.com/Layer/67c99b630d494b4da6dc5a0a11cb717e"
TEMPLATE = """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
CONSTRUCT { ?s a ?t ; rdfs:label ?l }
WHERE { ?s a ?t ; rdfs:label ?l . <#if headers.label??>FILTER(CONTAINS(STR(?l), "${headers.label}"))</#if> }
LIMIT 25"""


def sh(cmd, check=True, env=None, capture=True):
    r = subprocess.run(cmd, shell=isinstance(cmd, str), check=check, env=env,
                       stdout=subprocess.PIPE if capture else None, stderr=subprocess.STDOUT if capture else None,
                       text=True)
    return r.stdout.strip() if capture else ""


def http(method, url, body=None, headers=None, timeout=10):
    headers = dict({"Accept": "application/json"}, **(headers or {}))
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception:
        return 0, b""


def now_ms():
    return time.time() * 1000


# ------------------------------------------------------------------ image

def image_metrics(image):
    info = json.loads(sh([ENGINE, "image", "inspect", image]))[0]
    size = info["Size"]
    layers = len(info["RootFS"]["Layers"])
    tar = "/tmp/qtt-measure-image.tar" if not os.environ.get("TMPDIR") else os.path.join(os.environ["TMPDIR"], "qtt-measure-image.tar")
    sh([ENGINE, "save", "-o", tar, image])
    compressed = 0
    with open(tar, "rb") as f, open(os.devnull, "wb"):
        comp = gzip.compress(f.read(), compresslevel=6)
        compressed = len(comp)
    os.remove(tar)
    return {"image_bytes": size, "image_gzip_bytes": compressed, "layers": layers}


# ------------------------------------------------------------------ stack helpers

def stack(cmd, image, extra_env=None):
    env = dict(os.environ, QTT_IMAGE=image, QTT_DB="postgres", QTT_REDIS="true")
    if extra_env:
        env.update(extra_env)
    return sh([STACK, cmd], env=env)


def seed():
    ds = {"dataSourceId": "perf-ds", "timeOutSeconds": "30", "maxQueryHeaderLength": "8192",
          "username": "u", "password": "p", "url": "http://wiremock:8080", "validateCertificate": False}
    s, _ = http("POST", API + "/datasources", json.dumps(ds).encode(), {"Content-Type": "application/json"})
    assert s == 201, s
    boundary = "----qttperf"
    for i in range(SEED_ROUTES):
        rid = f"perf-route-{i:02d}"
        cache = "true" if i == 0 else "false"
        q = urllib.parse.urlencode({"routeParams": "httpMethodRestrict=GET,POST", "dataSourceId": "perf-ds",
                                    "description": "perf", "graphMartUri": GRAPHMART, "cacheEnabled": cache,
                                    "cacheTtlSeconds": "3600"})
        parts = []
        for name, val in (("freemarker", TEMPLATE), ("layers", LAYER)):
            parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"blob\"\r\n"
                         f"Content-Type: multipart/form-data\r\n\r\n{val}\r\n")
        body = ("".join(parts) + f"--{boundary}--\r\n").encode()
        s, b = http("POST", f"{API}/routes/{rid}?{q}", body,
                    {"Content-Type": f"multipart/form-data; boundary={boundary}"})
        assert s == 201, (s, b[:300])


def wait_until(fn, timeout):
    start = time.time()
    while time.time() - start < timeout:
        if fn():
            return True
        time.sleep(0.1)
    return False


def cold_start(image, runs):
    results = []
    for i in range(runs):
        sh([ENGINE, "rm", "-f", "qtt-it-app"])
        env = dict(os.environ, QTT_IMAGE=image, QTT_DB="postgres", QTT_REDIS="true")
        t0 = now_ms()
        sh([STACK, "start-app"], env=env)
        ok_api = wait_until(lambda: http("GET", API + "/settings/sysinfo", timeout=2)[0] == 200, 300)
        t_api = now_ms() - t0
        ok_q = wait_until(lambda: http("GET", QUERY + "/perf-route-01", timeout=10)[0] == 200, 300)
        t_query = now_ms() - t0
        if not (ok_api and ok_q):
            raise SystemExit(f"run {i}: not ready (api={ok_api}, query={ok_q})\n" + sh([ENGINE, "logs", "--tail", "80", "qtt-it-app"], check=False))
        results.append({"api_ms": round(t_api), "first_query_ms": round(t_query)})
        print(f"  cold start {i + 1}/{runs}: api {t_api:.0f} ms, first query {t_query:.0f} ms", file=sys.stderr)
    return results


def rss_mb():
    out = sh([ENGINE, "stats", "--no-stream", "--format", "{{.MemUsage}}", "qtt-it-app"])
    m = re.match(r"([\d.]+)\s*([kKMG]i?B)", out)
    val, unit = float(m.group(1)), m.group(2).lower()
    return round(val * {"kb": 1 / 1000, "kib": 1 / 1024, "mb": 1, "mib": 1, "gb": 1000, "gib": 1024}[unit], 1)


def load(url, seconds, concurrency):
    """Closed-loop load with scripts/tools/LoadGen.java (ApacheBench miscounts chunked keep-alive responses)."""
    out = sh(["java", os.path.join(ROOT, "scripts", "tools", "LoadGen.java"), url, str(seconds), str(concurrency)])
    r = json.loads(out.strip().splitlines()[-1])
    return {"rps": r["rps"], "p50_ms": r["p50_ms"], "p95_ms": r["p95_ms"], "p99_ms": r["p99_ms"],
            "errors": r["non2xx"] + r["bad_length"] + r["exceptions"], "requests": r["requests"]}


def write_table(results):
    md = os.path.join(ROOT, "docs", "perf", "measurements.md")
    with open(md, "w") as f:
        f.write("# QTT measurements\n\nProduced by `scripts/measure.py`; raw results in `results/`. Conditions: podman, "
                "QTT limited to 2 CPUs / 2 GB, PostgreSQL, Redis, mock Anzo (WireMock), 25 seeded routes, 10 cold "
                "starts; load from `scripts/tools/LoadGen.java` with 8 concurrent keep-alive clients for 30 s against "
                "an uncached route (every request calls the mock Anzo) and a cached route.\n\n"
                "| Label | Commit | Image MB | Image gz MB | Layers | API ready ms (median, min-max) "
                "| First query ms (median) | RSS idle MB | RSS after load MB | Uncached rps / p50 / p95 ms "
                "| Cached rps / p50 / p95 ms | Note |\n"
                "|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        for res in results:
            u, c = res["load_uncached"], res["load_cached"]
            f.write(f"| {res['label']} | {res['commit']} | {res['image_bytes'] / 1e6:.0f} | {res['image_gzip_bytes'] / 1e6:.0f} "
                    f"| {res['layers']} | {res['api_ready_ms_median']:.0f} ({res['api_ready_ms_min']}-{res['api_ready_ms_max']}) "
                    f"| {res['first_query_ms_median']:.0f} | {res['rss_idle_mb']} | {res['rss_after_load_mb']} "
                    f"| {u['rps']:.0f} / {u['p50_ms']:.1f} / {u['p95_ms']:.1f} | {c['rps']:.0f} / {c['p50_ms']:.1f} / {c['p95_ms']:.1f} "
                    f"| {res['note']} |\n")


def all_results():
    d = os.path.join(ROOT, "docs", "perf", "results")
    rs = [json.load(open(os.path.join(d, n))) for n in os.listdir(d) if n.endswith(".json")]
    return sorted(rs, key=lambda r: r["date"])


def load_only(args):
    slug = re.sub(r"[^a-z0-9-]+", "-", args.label.lower())
    path = os.path.join(ROOT, "docs", "perf", "results", slug + ".json")
    res = json.load(open(path))
    stack("up", args.image)
    try:
        seed()
        wait_until(lambda: http("GET", QUERY + "/perf-route-01", timeout=10)[0] == 200, 120)
        load(QUERY + "/perf-route-02", 15, args.concurrency)
        res["load_uncached"] = load(QUERY + "/perf-route-02", args.load_seconds, args.concurrency)
        load(QUERY + "/perf-route-00", 10, args.concurrency)
        res["load_cached"] = load(QUERY + "/perf-route-00", args.load_seconds, args.concurrency)
        res["rss_after_load_mb"] = rss_mb()
        res["load_remeasured"] = datetime.datetime.now().isoformat(timespec="seconds")
    finally:
        stack("down", args.image)
    json.dump(res, open(path, "w"), indent=2)
    write_table(all_results())
    print(json.dumps(res, indent=2))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--runs", type=int, default=10)
    ap.add_argument("--load-seconds", type=int, default=30)
    ap.add_argument("--concurrency", type=int, default=8)
    ap.add_argument("--note", default="")
    ap.add_argument("--load-only", action="store_true",
                    help="re-measure only load and RSS-after-load for an existing label (keeps image/startup numbers)")
    args = ap.parse_args()

    if args.load_only:
        return load_only(args)
    commit = sh("git rev-parse --short HEAD", check=False)
    dirty = bool(sh("git status --porcelain", check=False))
    print(f"[measure] {args.label}: image metrics", file=sys.stderr)
    res = {"label": args.label, "image": args.image, "commit": commit + ("+dirty" if dirty else ""),
           "date": datetime.datetime.now().isoformat(timespec="seconds"), "note": args.note}
    res.update(image_metrics(args.image))

    print("[measure] starting stack and seeding routes", file=sys.stderr)
    stack("up", args.image)
    try:
        seed()
        res["cold_start"] = cold_start(args.image, args.runs)
        api = [r["api_ms"] for r in res["cold_start"]]
        q = [r["first_query_ms"] for r in res["cold_start"]]
        res["api_ready_ms_median"] = statistics.median(api)
        res["api_ready_ms_min"], res["api_ready_ms_max"] = min(api), max(api)
        res["first_query_ms_median"] = statistics.median(q)
        res["first_query_ms_min"], res["first_query_ms_max"] = min(q), max(q)

        time.sleep(30)
        res["rss_idle_mb"] = rss_mb()
        # warm-up, then measured runs
        load(QUERY + "/perf-route-02", 15, args.concurrency)
        res["load_uncached"] = load(QUERY + "/perf-route-02", args.load_seconds, args.concurrency)
        load(QUERY + "/perf-route-00", 10, args.concurrency)
        res["load_cached"] = load(QUERY + "/perf-route-00", args.load_seconds, args.concurrency)
        res["rss_after_load_mb"] = rss_mb()
    finally:
        stack("down", args.image)

    os.makedirs(os.path.join(ROOT, "docs", "perf", "results"), exist_ok=True)
    slug = re.sub(r"[^a-z0-9-]+", "-", args.label.lower())
    with open(os.path.join(ROOT, "docs", "perf", "results", slug + ".json"), "w") as f:
        json.dump(res, f, indent=2)
    write_table(all_results())
    print(json.dumps(res, indent=2))


if __name__ == "__main__":
    import urllib.parse  # noqa: E402
    main()
