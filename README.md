<p align="center">
  <img src="docs/logo.png" alt="Comprix logo" width="160"/>
</p>

<h1 align="center">🛒 Comprix</h1>

---

<p align="center">
  <strong>Smart shopping lists — compare prices across stores. 100% offline.<br/>
  No account. No cloud. No internet.</strong>
</p>

<p align="center">
  <em>Compare preços entre mercados — listas de compras inteligentes.<br/>
  Sem login. Sem nuvem. Sem internet.</em>
</p>

<p align="center">
  <a href="https://github.com/nadabgfccvd/Comprix/actions/workflows/ci.yml"><img src="https://github.com/nadabgfccvd/Comprix/actions/workflows/ci.yml/badge.svg" alt="CI status"/></a>&nbsp;
  <img src="https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white" alt="Platform: Android 8.0+"/>&nbsp;
  <img src="https://img.shields.io/badge/release-v1.4.2-8A5CF6" alt="Release: v1.4.2"/>&nbsp;
  <img src="https://img.shields.io/badge/unit_tests-257_passing-4CAF50" alt="Unit tests: 257 passing"/>&nbsp;
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 1.9"/>&nbsp;
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4" alt="Jetpack Compose: Material 3"/>&nbsp;
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License: MIT"/>
</p>

## ✨ What it does

Comprix keeps your grocery lists and compares prices between the stores you register, so you always know where to buy each item — even splitting a single trip into the cheapest **mixed-cart** plan.

| | |
|---|---|
| 🛒 **Smart lists** | Free-text entry with a natural-language parser: *"2kg rice 4,99"* is understood as item, quantity, unit and price |
| 🏪 **Price comparison** | Side-by-side matrix of item prices across all your stores, with per-store totals |
| 🧮 **Mixed cart optimizer** | Suggests the cheapest combination of stores for the whole list (including kit/pack prices) |
| 📷 **Label OCR** | Scan product labels and barcodes (ML Kit, on-device) to extract name, price, quantity, nutrition info and allergens — **suggestions only**: every field is reviewed and confirmed by you before saving; always double-check the physical label |
| 💰 **Budgets** | Set a monthly/list budget and track spending with a progress bar |
| 🚨 **Allergy alerts** | Custom restrictions + allergen detection from label text — **informational reminders, not a guarantee**: text-based matching can produce false positives/negatives; the physical label always wins |
| 🗑️ **Trash & history** | 30-day recoverable trash, purchase history with CSV export, spending stats — plus a **full price-history CSV** (product, store, price, qty, date) in Settings |
| 💾 **Backup & restore** | Full local backup (JSON) to any file provider; automatic backups |
| 🎨 **4 themes** | Light, dark and high-contrast variants |
| 🔒 **No internet** | The APK requests no `INTERNET` permission — everything stays on your device |

## 📥 Download

Grab the latest APK from [**Releases**](https://github.com/nadabgfccvd/Comprix/releases) — every version from `v1.0` to `v1.4.2` is archived there. Detailed per-version history lives in [CHANGELOG.md](CHANGELOG.md) (pt-BR).

**Latest (v1.4.2):**

| File | For | Size |
|---|---|---|
| `comprix-v1.4.2-arm64-v8a-release.apk` | Most modern phones (2016+) — **recommended** | ~21 MB |
| `comprix-v1.4.2-armeabi-v7a-release.apk` | Older 32-bit phones | ~15 MB |
| `comprix-v1.4.2-release.apk` | Universal (all ABIs) | ~67 MB |
| `comprix-v1.4.2-x86_64-release.apk` / `-x86-` | Emulators | ~23 MB |

> `debug` variants are also attached for every release.
>
> **Upgrading from v1.4.1 or earlier?** See [Key rotation](#-key-rotation-v142) — v1.4.2 is signed with a new key, so Android requires uninstall → reinstall (your data is safe via the in-app backup).

## 🛠️ Build from source

Requirements: **JDK 17** and the **Android SDK** (platform 35 + build-tools 35.0.0). The Gradle wrapper is versioned — no Gradle install needed.

```bash
./gradlew testDebugUnitTest      # 257 JVM unit tests
./gradlew lintDebug              # Android lint (errors fail the build, also enforced by CI)
./gradlew assembleDebug          # debug APK
```

`assembleRelease` builds an **unsigned** APK out of the box. To produce a signed release, put your credentials in `keystore.properties` at the repo root (gitignored — never commit it):

```properties
storeFile=/absolute/path/to/comprix-release-v2.jks
storePassword=...
keyAlias=comprix-v2
keyPassword=...
```

See [`SECURITY.md`](SECURITY.md) for why the signing key is no longer in the repository.

## 🧱 Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3, 4 complete theme sets)
- **Room** for persistence, **ML Kit** for on-device OCR/barcodes
- Clean-ish architecture: `domain/` (pure Kotlin, fully unit-tested) · `data/` (Room repositories, backup) · `presentation/` (Compose screens + ViewModels)
- Hand-rolled pt-BR quantity parser (`domain/parser/`) covered by a 257-test suite

## 🤖 CI

Every push and pull request runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml) on GitHub Actions: **257 unit tests → Android lint (errors are blocking) → debug build**. Lint is a hard gate (`abortOnError = true`), including the lint-vital pass that release builds run. Pushing a `v*` tag triggers [`.github/workflows/release.yml`](.github/workflows/release.yml): tests + signed release build + APK upload (signing via repository secrets; unsigned without them).

## 📦 Repository structure

```
app/src/main/java/br/com/comprix/   # app source (domain / data / presentation)
.github/workflows/                  # CI: tests + lint + build on every push/PR
docs/                               # internal docs, logo, social preview, key rotation guide
```

## 🔐 Key rotation (v1.4.2)

Releases up to **v1.4.1** were signed with a key that was auto-generated and committed to this repository (fixed passphrase, public code). That key is considered **compromised** and has been removed from the repo and its git history; it must never sign production builds again.

- **v1.4.2+** is signed with a new **private** key kept outside the repository (loaded via gitignored `keystore.properties`).
- Because the signing identity changed, Android blocks direct updates from ≤ v1.4.1: **uninstall the old app, install v1.4.2**. Export an in-app **backup** first (Configurações → Backup) and restore after installing — everything is local, nothing is lost.
- Historical releases remain on the Releases page as artifacts of their time; their APKs are still installable but should be treated as legacy.

Details in [`docs/ROTACAO-DE-CHAVE-v1.4.2.md`](docs/ROTACAO-DE-CHAVE-v1.4.2.md) (pt-BR) and [`SECURITY.md`](SECURITY.md).

## 📄 License

Released under the **[MIT License](LICENSE)** — free to use, modify and distribute. The UI language is Brazilian Portuguese (`pt-BR`).

## 🔒 Security

Found a vulnerability? Read [`SECURITY.md`](SECURITY.md). TL;DR: 100% offline app, no network permission, no telemetry — report issues privately via [Security Advisories](https://github.com/nadabgfccvd/Comprix/security/advisories/new).
