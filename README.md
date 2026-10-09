# Comprix

**Smart shopping lists + price comparison across stores. 100% offline Android app.**

![Platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)
![Release](https://img.shields.io/badge/release-v1.4.1-8A5CF6)
![Tests](https://img.shields.io/badge/unit_tests-253%20passing-4CAF50)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)

> 🇧🇷 App in Brazilian Portuguese · **Compareça**: lista de compras + comparador de preços entre mercados, sem login, sem nuvem, sem internet.

---

## ✨ What it does

Comprix keeps your grocery lists and compares prices between the stores you register, so you always know where to buy each item — even splitting a single trip into the cheapest **mixed-cart** plan.

| | |
|---|---|
| 🛒 **Smart lists** | Free-text entry with a natural-language parser: *"2kg rice 4,99"* is understood as item, quantity, unit and price |
| 🏪 **Price comparison** | Side-by-side matrix of item prices across all your stores, with per-store totals |
| 🧮 **Mixed cart optimizer** | Suggests the cheapest combination of stores for the whole list (including kit/pack prices) |
| 📷 **Label OCR** | Scan product labels and barcodes (ML Kit, on-device) to extract name, price, quantity, nutrition info and allergens |
| 💰 **Budgets** | Set a monthly/list budget and track spending with a progress bar |
| 🚨 **Allergy alerts** | Custom restrictions + allergen detection from labels |
| 🗑️ **Trash & history** | 30-day recoverable trash, purchase history with CSV export and spending stats |
| 💾 **Backup & restore** | Full local backup (JSON) to any file provider; automatic backups |
| 🎨 **4 themes** | Light, dark and high-contrast variants |
| 🔒 **No internet** | The APK requests no `INTERNET` permission — everything stays on your device |

## 📥 Download

Grab the latest APK from [**Releases**](https://github.com/nadabgfccvd/Comprix/releases) — every version from `v1.0` to `v1.4.1` is archived there.

**Latest (v1.4.1):**

| File | For | Size |
|---|---|---|
| `comprix-v1.4.1-arm64-v8a-release.apk` | Most modern phones (2016+) — **recommended** | ~21 MB |
| `comprix-v1.4.1-armeabi-v7a-release.apk` | Older 32-bit phones | ~15 MB |
| `comprix-v1.4.1-release.apk` | Universal (all ABIs) | ~67 MB |
| `comprix-v1.4.1-x86_64-release.apk` / `-x86-` | Emulators | ~23 MB |

> `debug` variants are also attached for every release. If a version older than v1.4.1 ships a `.tgz`, extract it with `tar -xzf` — the original `.apk` filenames are preserved inside.

## 🛠️ Build from source

Requirements: **JDK 17** and the **Android SDK** (platform 35 + build-tools 35.0.0). The Gradle wrapper is versioned — no Gradle install needed.

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # or set ANDROID_HOME
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # signed release APK (R8 + resource shrink)
./gradlew testDebugUnitTest      # 253 JVM unit tests
```

Release builds are auto-signed with the development keystore in `keystore/` (kept in the repo on purpose: it keeps APK updates installable over previous versions).

## 🧱 Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3, 4 complete theme sets)
- **Room** for persistence, **ML Kit** for on-device OCR/barcodes
- Clean-ish architecture: `domain/` (pure Kotlin, fully unit-tested) · `data/` (Room repositories, backup) · `presentation/` (Compose screens + ViewModels)
- Hand-rolled pt-BR quantity parser (`domain/parser/`) covered by a 253-test suite

## 📦 Repository structure

```
app/src/main/java/br/com/comprix/   # app source (domain / data / presentation)
backups/fonte/                      # historical source snapshots (v1.0 rebuild phases → v1.4.1)
docs/                               # internal project docs
keystore/                           # development release keystore
```

## 📄 Notes

Personal project, built iteratively with heavy unit-test coverage and UX/accessibility polish. UI language is Brazilian Portuguese (`pt-BR`).
