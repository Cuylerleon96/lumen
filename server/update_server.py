#!/usr/bin/env python3
"""Tiny LOCAL update server for Mobile Slicer (OrcaMicro).

Serves a build directory (default ~/mslicer_updates) over HTTP, exposing:
  /update/info.json   version manifest (version, versionCode, sha256, notes)
  /update/app.apk     the APK

The app's About -> "Check for updates" fetches info.json, downloads app.apk, verifies the
sha256 and installs it. Local only — bind on an address the phone can reach.

Run on the build host (the build host):  python3 server/update_server.py --port 8799
"""
import argparse, functools, http.server, os


def main():
    ap = argparse.ArgumentParser(description="Mobile Slicer update server")
    ap.add_argument("--dir", default=os.path.expanduser("~/mslicer_updates"))
    ap.add_argument("--port", type=int, default=8799)
    ap.add_argument("--host", default="0.0.0.0")
    a = ap.parse_args()
    os.makedirs(a.dir, exist_ok=True)
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=a.dir)
    srv = http.server.ThreadingHTTPServer((a.host, a.port), handler)
    print(f"Mobile Slicer updates: serving {a.dir} on http://{a.host}:{a.port}")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
