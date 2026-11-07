# Lumen — session handoff brief

Paste this whole block as the first message of a new session to continue the build.

---

**Project:** **Lumen** — an open-source, on-device (Android), dark/modern mobile **3D-print slicer** covering **both FDM and resin (MSLA)**, no cloud/account. License **AGPL-3.0-or-later** (inherited; non-negotiable). One unified app where FDM / resin / experimental slicing methods are **pluggable backends**.

**Repo (local):** `~/Projects/mobile-slicer` (git; commit with `-c user.name='Cuyler' -c user.email='cuyler@local'`). Now renamed: Kotlin package **`dev.lumen.slicer`**, `rootProject.name` = Lumen.
**Repo (remote, private):** `https://github.com/Cuylerleon96/lumen`.
**Vault note:** `~/Documents/Obsidian Vault/Projects/Mobile Slicer.md`.

**Engines (both AGPL, cross-compiled for arm64, shipped as prebuilt `.so` in `app/src/main/jniLibs/arm64-v8a/`):**
- FDM: OrcaSlicer 2.4.2 `libslic3r` → `liborcamicro_engine.so` (JNI shim `native/bridge/jni.cpp`).
- Resin: PrusaSlicer 2.9.7 SLA `libslic3r` → `libsla_engine.so` (JNI shim `native/sla/sla_jni.cpp`).

**Build host:** build host, tailnet name **`build-host`**, ssh alias **`build-host`** (login shell is **fish** — always `ssh build-host bash -s <<'SH' … SH`; append `</dev/null` to every `adb` call). Build tree `~/Projects/mobile-slicer-src`. NDK r27c `27.2.12479018`. Heavy artifacts at `<build-dir>`. RAM-tight (low-RAM) → `ORCAMICRO_JOBS=3..4`, run links **detached** (`setsid nohup`) so an SSH drop can't kill them; serialize two heavy links.

**Gradle (on build host):** `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx3072m -Dkotlin.daemon.jvmargs=-Xmx1024m -PorcaResourcesDir="$R/native/build/orcaslicer/resources" :core:test assembleDebug`

**SAFE SYNC (user correction, verbatim: "don't risk our data like that again"):** NEVER `rsync --delete` at a repo/build tree. Sync one subtree at a time, structure-preserving, `--dry-run` first, exclude `jniLibs/`. **Multi-source `rsync a/x b/x host:dest/` COLLIDES on basenames** — sync each file into its own dir. After a package move, sync the WHOLE renamed source tree, remove the stale old-package source dirs on the host, and clean (`rm -rf core/build app/build`).

**Update server:** build host, `systemd-run --user --unit=mslicer-update python3 server/update_server.py --dir ~/mslicer_updates --port 8799`; `http://build-host:8799` (tailnet). Manifest `update/info.json` (`version`,`versionCode`,`sha256`,`url`,`notes`) + `update/app.apk`. Publishing = copy the APK + bump versionCode. **Latest published: v0.7.0.** Taildrop: `tailscale file cp <apk> phone:` (bash -s, phone must be online).

**Working state:** Lumen **v0.7.0** on the Pixel 6; source pushed to the private GitHub. `:core:test` green (≈70 tests).

**DONE (19 items, each built→unit-tested→committed→published):** estimation (vol/wt/cost) · resin placement (move/rotate/scale/mirror/fit) · resin profile DB · island detection · resin-trap detection · **layer repair** (speck removal + pinhole fill) · OBJ import · 3MF import · anti-aliasing · **CTB writer** · **GOO writer** + SL1→CTB / SL1→GOO converters · output-format override picker · hollowing · support settings · project save/load (`.osproj`) · calibration generator · model library/recents · **real RGB565 previews** (just added) · Elegoo Saturn (4K) + Saturn 2 (8K) printer profiles.

**TRACKER — what remains** (`docs/FEATURES.md`):
1. **Drain holes / hollow blockers** — needs a mesh CSG (boolean) engine; core has none. Biggest functional gap for resin.
2. **Interactive support add/edit/paint** — big touch UI + hit-testing.
3. **Remaining output formats** — PW0/PWS/PhotonS/CBDDLP/… (each needs a pulled spec, like GOO: open UVTools `FileFormats/*.cs`).
4. **Touch tool-rail / loupe** (design in `docs/touch-interaction.md`).
5. **On-disk path rename** (`~/Projects/mobile-slicer` → `lumen`) — cosmetic, touches every path.
6. FDM calibration towers; G-code post-processing; full library UI.

**Format writers are clean-room from open specs** (CTB = catibo spec; GOO = open UVTools `GooFile.cs`). CTB is little-endian, GOO is big-endian. Fixed-length ASCII fields copy `min(srcLen,len)` — NOT `len-1`. **A package rename forces a native rebuild** (JNI symbol `Java_<pkg>_<Class>_<method>`); the engine builds its own copy of `*_jni.cpp` inside the vendored tree — update THAT copy too.

**Skills to load:** `slicer-notes` (+ references), `build-notes`, `remote-worker`, `obsidian`. Load `slicer-notes` first — it has the format recipes and the rename/sync pitfalls.

**Recommended next step:** drain holes (item 1) — start by adding a small BSP-based mesh CSG in `:core`, subtract a cylinder at a tapped point, unit-test the volume change, then a touch UI to place holes.

**Standing preferences:** CONCISE > verbose. Scientific method (hypothesis→test→evidence). Prefer config/DB edits over APIs. Show every real screenshot/frame. Don't touch network/VPN config; don't complete OAuth logins.
