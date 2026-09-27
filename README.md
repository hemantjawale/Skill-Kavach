# Skill-Kavach (SurakshaSetu)

**AR-Based Vocational Training Simulator for Industrial Safety**  
*SIH 2026 Problem Statement 26041 — Mining & Manufacturing Sector (Jharkhand)*

---

## 📋 Overview

**Skill-Kavach** is an offline-first, multilingual, ARCore-powered industrial safety training and certification ecosystem. Designed specifically for workers and safety supervisors in high-risk mining, steel, and manufacturing industries, Skill-Kavach converts abstract safety protocols into immersive, physically calibrated 3D Augmented Reality simulations directly on standard Android mobile devices.

---

## 🎯 Problem & Solution

### The Problem
* High fatality rates in mining and heavy manufacturing due to improper entry into confined spaces, fire panics, and gas leak mismanagements.
* Traditional classroom safety training lacks practical spatial awareness and behavioral feedback.
* Language barriers: many industrial workers in Jharkhand speak Hindi or Santali (Ol Chiki script), whereas standard safety manuals are exclusively in English.
* Unreliable network connectivity in deep open-pit mines, underground tunnels, and remote manufacturing plants prevents cloud-only apps from functioning.

### The Solution
* **Immersive AR Simulations**: Realistic Fire Response (PBR materials, extinguisher PASS sequence, sweep gestures) and Gas Leak / Confined Space entry (entry hatch, multi-gas detector LCD, rescue tripod, standby attendant, explosion-proof ventilation blower).
* **100% Offline-First Architecture**: Complete training, practical assessment, local scoring, and cryptographic certificate generation operate fully offline. Submissions are queued in an encrypted Room SQLite Outbox and automatically synced via WorkManager when connectivity resumes.
* **Complete Multilingual & Spoken Voice Support**: 100% UI localization across **English**, **Hindi (हिन्दी)**, and **Santali (ᱥᱟᱱᱛᱟᱲᱤ - Ol Chiki script)** with integrated `SafetyMitraTutor` voice assistance.
* **Cryptographic Certificate Vault**: Tamper-proof certificates signed using Android Keystore ECDSA (P-256) with embedded QR codes, SHA-256 hash-chain ledger, and offline verification.
* **Enterprise Supervisor Portal**: Web-based manager console for worker tracking, assessment scoring analytics, certificate validation, and compliance auditing.

---

## 🏗️ Architecture & Technology Stack

```
                               ┌─────────────────────────────────────────┐
                               │           Skill-Kavach Android          │
                               │  (Kotlin, Jetpack Compose, ARCore PBR)  │
                               └────────────────────┬────────────────────┘
                                                    │
                 ┌──────────────────────────────────┴──────────────────────────────────┐
                 │                                                                     │
                 ▼                                                                     ▼
   ┌──────────────────────────┐                                          ┌──────────────────────────┐
   │    AR Training Engine    │                                          │  Local Offline Vault     │
   │  - Fire (PASS Protocol)  │                                          │  - Room DB (AES-256-GCM) │
   │  - Gas (Confined Space)  │                                          │  - Encrypted Outbox      │
   │  - Hazard Simulator      │                                          │  - ECDSA Certificate Vault│
   └─────────────┬────────────┘                                          └─────────────┬────────────┘
                 │                                                                     │
                 └──────────────────────────────────┬──────────────────────────────────┘
                                                    │ WorkManager Sync (P2P / Web)
                                                    ▼
                               ┌─────────────────────────────────────────┐
                               │        Express & PostgreSQL API         │
                               │    (JWT Auth, Zod Validation, OTP)      │
                               └────────────────────┬────────────────────┘
                                                    │
                                                    ▼
                               ┌─────────────────────────────────────────┐
                               │       React Supervisor Dashboard        │
                               │     (Worker Analytics & Compliance)     │
                               └─────────────────────────────────────────┘
```

| Layer | Technologies |
|---|---|
| **Android App** | Kotlin 1.9+, Jetpack Compose, ARCore 1.38+, Custom PBR Shader Engine, Room SQLite, WorkManager, Android Keystore |
| **Backend API** | Node.js, Express, PostgreSQL, Zod schema validation, JWT with OTP authentication, Security Headers |
| **Admin Web Console** | React 18, Vite, TailwindCSS, Chart.js / Recharts |
| **Crypto & Security** | ECDSA (P-256), SHA-256 Hash-Chain Ledger, AES-256-GCM Encrypted Storage |

---

## 🥽 AR Training Modules

### 1. Fire & Explosion Response Module
* **Physical Scale Calibration**: 1:1 scale equipment (1.2m cylinder height, 0.45m base diameter) with realistic PBR metallic shaders, gauge pressure needles, and safety pin tags.
* **8-Step PASS Protocol**:
  1. Identify Emergency Exit
  2. Raise Alarm / Alert Supervisor
  3. Select Correct Extinguisher Type (CO₂, Foam, Dry Powder)
  4. Pull Safety Pin
  5. Aim Nozzle at Fire Base
  6. Squeeze Extinguisher Lever
  7. **Sweep Gesture**: Drag touch gesture across screen with real-time particle extinction physics and smoke dissipation.
  8. Evacuate via Designated Exit Route.

### 2. Gas Leak & Confined Space Entry Module
* **Industrial Environment Assets**:
  * Confined Space Entry Hatch with warning indicators.
  * Multi-Gas Detector LCD displaying real-time simulated $O_2$ (20.9%), $H_2S$ (0 ppm), $CO$ (0 ppm), and $LEL$ (0%) levels.
  * 3-Leg Heavy Duty Rescue Tripod with winch & safety harness anchor.
  * Standby Attendant NPC & Permit-to-Work clipboard board.
  * Portable Explosion-Proof Ventilation Blower (`ventilationBlower`) featuring animated fan rotation and directional air flow exhaust diffusion.
* **Dynamic Hazard Physics**: `HazardDiffusionSimulator` computes 3D volumetric gas dispersion, gas layering (heavy vs light gases), dynamic alarm thresholds, and airflow dissipation upon blower activation.

---

## 🌐 Multilingual & Voice System

Full localized user interface, AR HUD overlays, and voice prompts across:
1. **English** (`values`)
2. **Hindi - हिन्दी** (`values-hi`)
3. **Santali - ᱥᱟᱱᱛᱟᱲᱤ (Ol Chiki Script)** (`values-sat`)

### Voice Tutor (`SafetyMitraTutor`)
* Spoken TTS instruction capability.
* Graceful fallback reporting (`TtsResult.UNSUPPORTED_LANGUAGE`) when device lacks specific language packs, ensuring zero app crashes and clean UI prompt fallbacks.

---

## 📜 Cryptographic Certificate Vault & QR Verification

Certificates are generated locally upon passing an assessment and secured cryptographically:
* **Signing**: Signed via Android Keystore ECDSA (P-256) private key.
* **Hash-Chain Ledger**: Each certificate contains its own SHA-256 hash plus a `prevHash` reference to guarantee tamper resistance.
* **QR Verification**: Built-in camera QR scanner verifies certificate authenticity, worker ID, issue timestamp, and expiration without requiring cloud access.
* **Tamper Tests**: Comprehensive unit tests (`CertificateVaultTest.kt`) verify that modifying scores, worker IDs, or timestamps instantly flags `FAIL_TAMPERED`.

---

## 📱 Mobile App Offline-First Workflow

```text
User Starts Assessment (Offline)
             ↓
Completes Quiz + Practical AR Steps
             ↓
AssessmentEngine Calculates Weighted Score
             ↓
Certificate Cryptographically Signed & Generated
             ↓
Record & Certificate Stored in Encrypted Room Database
             ↓
Encrypted Outbox Item Created
             ↓
[Network Reconnected]
             ↓
WorkManager Triggers Background Sync
             ↓
Server Validates & Acknowledges Operation
             ↓
Outbox Item Marked Synced
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
* **Android Studio**: Jellyfish / Koala or newer with JDK 17.
* **Android SDK**: API Level 34 (Android 14) compile SDK, Minimum API 29 (Android 10).
* **Node.js**: v18.x or v20.x for Backend & Admin Console.
* **AR Device**: ARCore-supported Android device (or Android Emulator with camera pass-through).

### Release Version Information
* **Version Name**: `1.0.0-SIH2026`
* **Version Code**: `100`

### 1. Build Android APKs (Debug & Release)
```bash
# Clone repository
git clone https://github.com/hemantjawale/Skill-Kavach.git
cd Skill-Kavach

# Run Unit Tests
./gradlew testDebugUnitTest

# Assemble Debug & Release APKs
./gradlew clean assembleDebug assembleRelease
```
* **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
* **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`

### 2. Run Local Backend (Express + Node)
```bash
cd backend
npm install
node scripts/dev-local.js
```
*Runs API server locally at* `http://localhost:8080`

### 3. Run Admin Web Console
```bash
cd admin
npm install
npm run dev
```
*Opens Supervisor Portal at* `http://localhost:5173`

---

## 🧪 Demonstration Steps for SIH Evaluators

1. **Install APK**: Install `app-debug.apk` on an ARCore-compatible Android phone.
2. **Language Selection**: Select **English**, **Hindi (हिन्दी)**, or **Santali (ᱥᱟᱱᱛᱟᱲᱤ)** from the top app toolbar.
3. **Fire AR Module**:
   * Open **Training → Fire & Explosion Response → Start AR Training**.
   * Scan floor until placement ring turns green. Tap **Place Equipment**.
   * Perform PASS sequence: Tap Exit → Tap Alarm → Select Extinguisher → Pull Pin → Aim Base → Squeeze Lever → **Perform Sweep Gesture** across the flame.
4. **Gas Leak AR Module**:
   * Open **Training → Gas Leak & Confined Space Protocol → Start AR Training**.
   * Place industrial confined space scene (Hatch, Gas Detector LCD, Tripod, Blower).
   * Verify real-time Gas Detector LCD readings ($O_2$, $H_2S$, $CO$, $LEL$) and turn on ventilation blower to dissipate gas.
5. **Assessment & Offline Certificate**:
   * Complete the Assessment Mode evaluation.
   * View the instantly generated cryptographic QR certificate.
   * Toggle Airplane Mode ON/OFF to witness background Outbox WorkManager sync.

---

## ⚠️ Safety & Simulation Disclaimer

> [!IMPORTANT]
> **Skill-Kavach is a virtual educational and vocational training simulator.**
> * **Simulation Scope**: This application provides spatial 3D procedural simulations of industrial safety protocols. It does **NOT** perform real-time optical camera fire detection, physical atmospheric gas sensing, or real environment hazard monitoring.
> * **Certification Disclaimer**: Certificates issued by the system represent in-app practical training progress and offline skill assessment results signed via Android Keystore ECDSA. Completing this simulation does **NOT** confer statutory government safety licenses or replace mandatory site-specific safety inductions.
> * **Voice & Language Disclaimer**: Spoken voice instructions are driven by device Text-to-Speech (TTS) engines. `SafetyMitraTutor` provides interactive audio prompts in supported languages and falls back gracefully to visual text prompts in Ol Chiki (`ᱥᱟᱱᱛᱟᱲᱤ`) when device TTS language packs are absent.

---

## 🛡️ License & Asset Attribution

* **License**: Open Source under Apache License 2.0 / MIT.
* **3D Assets & Shaders**: Custom procedural PBR shader network and low-poly industrial mesh generators (`IndustrialMeshes.kt`, `GasEffectRenderer.kt`, `FireEffectRenderer.kt`). No unlicensed 3D assets used.
