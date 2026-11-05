# Magister Van Gister - Dutch Magister Legacy Downgrader

Magister Van Gister is an open-source Android utility built with Kotlin and Jetpack Compose (Package ID: `com.epicmemazguy.magistervangister`) that enables Dutch students and educators to archive, manage, and downgrade their Magister application to legacy versions fetched from GitHub releases. It features native Split APK and XAPK package installation via Android's `PackageInstaller` Session API, rich Dutch localization, zero proprietary dependencies for clean F-Droid builds, and modern dark gradient styling.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **F-Droid Build Purity**: To ensure full compliance with F-Droid automated inclusion guidelines, all closed-source or Google Play dependencies (Firebase, Google Services, closed analytics) will be removed or commented out. The application relies strictly on standard Android SDK, Jetpack Compose, OkHttp/Retrofit for GitHub API calls, and local unzipping/installation utilities.

- **Confirmed Repository Target**: `EpicMemazGuy/magister-apk-archive-releases` (with configurable custom repository endpoint support in settings and offline fallback metadata for versions 2.7.0 through 2.9.2).
- **Package Installation Engine**: Uses native `android.content.pm.PackageInstaller` Session API (`MODE_FULL_INSTALL`) streaming base and split config APKs directly to Android's native installer prompt without needing external helper apps or root access.
- **UI Aesthetic**: Rich Dutch Royal Navy & Electric Cyan linear gradient theming over a sleek dark canvas, satisfying the gradient design directive.

---

### 1. Overview & Core Concept

- **What It Does**: Connects to the community Magister release repository (`EpicMemazGuy/magister-apk-archive-releases`), inspects release assets (`.apk` and `.xapk` bundles), streams downloads with real-time byte progress, extracts multi-split APK architectures in cache memory, and initiates the Android package installation session to downgrade or sidegrade the Magister client.
- **Target Audience / Persona**: Dutch secondary school students, parents, and teachers who need legacy features, interface workflows, or stability from older Magister releases (e.g. v2.7.0 to v2.9.2) that were altered or removed in newer updates.
- **Key Value**: Painless downgrade flow without ADB commands, root requirements, or suspicious third-party APK app stores, packaged in an auditable open-source tool compliant with GPL-3.0.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Dashboard & Version Browser**: The user opens the app and sees the status of the currently installed Magister app (Package: `nl.topicus.magister.parent` or `school.magister.app` / detection if installed, active version name and version code).
  2. **Release Feed**: A scrollable list of Magister releases (e.g. 2.9.2, 2.9.0, 2.8.1, 2.8.0, 2.7.2, 2.7.0) showing release tags, publish dates, asset types (`.xapk` bundle vs `.apk`), asset sizes, and Dutch changelog highlights.
  3. **Download & Extraction**: Tapping "Downloaden & Installeren" initiates a streaming download with real-time percentage, downloaded MBs, and extraction progress indicator.
  4. **PackageInstaller Session Hand-off**: For `.xapk` files, the internal extractor parses the ZIP container, discovers `base.apk` and configuration splits (`config.xxhdpi.apk`, `config.arm64_v8a.apk`, etc.), writes them into the PackageInstaller session, and fires the system package installation confirmation dialog.
  5. **Repository & Settings Modal**: Quick dialog to switch GitHub repository, clear APK cache, or review open-source F-Droid license and permissions (`REQUEST_INSTALL_PACKAGES`).

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Utilitarian yet polished modern dark theme with prominent vibrant gradients.
  - *Gradient Palette*:
    - Hero Brand Gradient: Deep Dutch Navy (`#0A1128`) fading into Royal Cobalt (`#1C3782`) and Electric Cyan (`#00D2FF`).
    - Accent Gradients: Vivid Teal-Cyan linear brush for download progress bars and action buttons (`#00F2FE` → `#4FACFE`).
    - Card Backgrounds: Dark slate surfaces (`#131B2E` with 1dp border `#243354`) with subtle diagonal lighting accents.
  - *Typography & Spacing*: Clean modern typography with bold version headers, distinct badges for APK vs XAPK, and high-contrast metadata chips.
  - *Dutch Localized Copy*: Intuitive terminology throughout ("Versies", "Geïnstalleerd", "Downloaden", "Pakket voorbereiden", "Installatie starten", "Opslag wissen", "Instellingen").

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native PackageInstaller Session API vs External Intent**:
  - *Chosen Approach*: `PackageInstaller.SessionParams.MODE_FULL_INSTALL` streaming both standalone APKs and split APK packages found in XAPK archives directly.
  - *Why*: Modern Android apps (including Magister) are distributed as Android App Bundles (AAB/XAPK) with split architectures. Standard `ACTION_VIEW` intent cannot install split configs. The `PackageInstaller` session handles both single APKs and split bundles seamlessly.
  - *Alternative Passed Over*: Forcing user to install third-party SAI (Split APKs Installer) or requiring root.
- **Decision 2: F-Droid Build Compatibility**:
  - *Chosen Approach*: Clean Gradle structure, open-source libraries only (Retrofit, OkHttp, Kotlin Coroutines, Jetpack Compose, Room for local caching), no proprietary trackers or Google Play Services libraries.
  - *Why*: F-Droid will immediately reject repositories containing non-free SDKs or Google Play binaries.
- **Decision 3: Hybrid Online/Offline Catalog Architecture**:
  - *Chosen Approach*: Dynamic fetch via GitHub REST API (`/repos/EpicMemazGuy/magister-apk-archive-releases/releases`) backed by a pre-seeded local catalog for tags `Release-2.7.0-to-2.9.2`.
  - *Why*: Protects users from GitHub API rate limiting (60 requests/hr for unauthenticated IPs) while still downloading real release assets and allowing repository updates.

---

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────┐
│                   MainActivity.kt                        │
│   (Compose UI, Scaffold, Gradient Header, Navigation)   │
└────────────────────────────┬─────────────────────────────┘
                             │ StateFlow (UiState)
                             ▼
┌──────────────────────────────────────────────────────────┐
│                   MainViewModel.kt                       │
│  - Detects installed Magister version                    │
│  - Fetches GitHub releases via ReleaseRepository         │
│  - Manages download states & triggers XapkInstaller      │
└──────────────┬────────────────────────────┬──────────────┘
               │                            │
               ▼                            ▼
┌──────────────────────────────┐ ┌─────────────────────────┐
│     ReleaseRepository.kt     │ │    XapkInstaller.kt     │
│  - GitHub API / OkHttp       │ │  - PackageInstaller     │
│  - Local Cache / Fallback    │ │  - ZIP/XAPK stream      │
│  - Asset download stream     │ │  - Session commit       │
└──────────────────────────────┘ └─────────────────────────┘
```

- **Core Data Classes**:
  - `MagisterRelease`: Version name, version code, tag name, published date, body description, asset URL, file size, file type (`APK` or `XAPK`).
  - `DownloadState`: Idle, Downloading(progress, downloadedBytes, totalBytes), Extracting, Installing, Error(message), Success.
- **Permissions**:
  - `android.permission.INTERNET`
  - `android.permission.REQUEST_INSTALL_PACKAGES`
- **Session Installation Lifecycle**:
  1. Verifies `packageManager.canRequestPackageInstalls()` and navigates to system settings if permission is not yet granted.
  2. Creates `PackageInstaller.SessionParams(MODE_FULL_INSTALL)`.
  3. Writes stream entries (`base.apk`, `split_config.*.apk`).
  4. Commits session with a `PendingIntent` receiver to capture success or downgrade collision codes.
