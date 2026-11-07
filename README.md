<div align="center">
  <img src="src/main/resources/icons/app-icon.png" alt="ERONIX emergency department icon" width="128" height="128">

  <h1>ERONIX</h1>

  <p><strong>A live emergency-department simulation for desktop and Android.</strong></p>

  <p>
    <a href="https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/tag/v1.0.3"><strong>Download ERONIX v1.0.3</strong></a>
    &nbsp;·&nbsp;
    <a href="#screenshots">Screenshots</a>
    &nbsp;·&nbsp;
    <a href="#how-it-works">How it works</a>
    &nbsp;·&nbsp;
    <a href="#build-from-source">Build from source</a>
  </p>

  <p>
    <a href="https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/EngOmarAbd-Elaziz/ERONIX?style=for-the-badge&color=3157d5"></a>
    <img alt="Android 14+" src="https://img.shields.io/badge/Android-14%2B-3ddc84?style=for-the-badge&logo=android&logoColor=white">
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-ec2025?style=for-the-badge&logo=openjdk&logoColor=white">
  </p>
</div>

---

## What is ERONIX?

ERONIX is an educational emergency-department simulation. Configure a small
department, start a live simulation, and follow patients as they arrive, wait
for space, receive treatment, and leave.

The project makes operating-systems concepts visible in a practical example:
threads, synchronization, bounded buffers, semaphores, and coordination over
shared resources. It is both a working simulation and a learning tool for
students exploring concurrent programming.

> **Scope:** ERONIX is an educational model, not a medical product. Its results
> do not certify that a real hospital or emergency department is safe, staffed,
> or operationally ready.

## Screenshots

<a id="screenshots"></a>

<p align="center">
  <img src="docs/screenshots/dashboard-preview.svg" alt="ERONIX desktop dashboard preview" width="100%">
</p>

<p align="center"><sub>Illustrative dashboard preview. The displayed simulation values are sample UI content.</sub></p>

The desktop dashboard shows live patient statistics, waiting-room occupancy,
doctor and treatment-room states, and a chronological event timeline. The
Android companion organizes the same simulation into Home, Waiting, Team,
Events, and Settings pages.

## Download ERONIX v1.0.3

Choose the file for your device. These are platform-specific installers; do not
try to install a package on an unrelated operating system.

| File | For | What it is and how to use it |
|---|---|---|
| [**ERONIX.msi**](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.msi) | Windows | Windows Installer package. Download and open it to install ERONIX. |
| [**ERONIX.rpm**](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.rpm) | Fedora and other RPM-based Linux | Native RPM package. On Fedora, install with `sudo dnf install ./ERONIX.rpm`. |
| [**ERONIX.deb**](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.deb) | Ubuntu, Debian, and other DEB-based Linux | Debian package. On Ubuntu/Debian, install with `sudo apt install ./ERONIX.deb`. |
| [**ERONIX.apk**](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.apk) | Android 14 (API 34) or newer | Android application package. Open it on the device and approve installation if Android asks. |
| [Source code (ZIP)](https://github.com/EngOmarAbd-Elaziz/ERONIX/archive/refs/tags/v1.0.3.zip) | Developers | Compressed copy of the source at v1.0.3; it is not an installer. |
| [Source code (tar.gz)](https://github.com/EngOmarAbd-Elaziz/ERONIX/archive/refs/tags/v1.0.3.tar.gz) | Developers | The same source snapshot in a tar archive; it is not an installer. |

The `.msi`, `.rpm`, and `.deb` files install the desktop application with its
bundled Java runtime. Fedora users should use the RPM, not the DEB. The v1.0.3
release does not include an AppImage.

For future releases, use **Check for updates** inside the app to open the
release page when an update is available. The user confirms and installs the
downloaded package; updates are not installed silently.

## How it works

The desktop and Android interfaces use the same Java simulation engine. The
interface observes real engine state; it does not invent a separate animation
of patients or doctors.

1. **Patients arrive** according to the configured arrival interval. Each
   patient is represented by a worker thread.
2. **The waiting room is a bounded buffer.** Its capacity is limited. Custom
   `empty`, `full`, and `mutex` semaphores coordinate patients entering and
   doctors taking patients; patients wait outside when it is full.
3. **Doctors run concurrently.** A doctor takes a waiting patient and requests
   a treatment room. If no room becomes available in time, the patient returns
   to the waiting process.
4. **Treatment rooms are shared resources.** A counting semaphore controls
   access. When treatment finishes, the room is released for the next patient.
5. **The dashboard receives snapshots and events** from the engine and updates
   live statistics, staff and room states, and the event timeline.

### Configure a simulation

The desktop application supports 1–30 waiting seats, 1–20 doctors, 1–200
patients, and 1–20 treatment rooms. Arrival intervals can be configured from
0 to 60 seconds; treatment duration is 1–5 seconds. The Android app also
provides configurable simulation settings, subject to its input controls.

## Project structure

| Path | Purpose |
|---|---|
| `src/main/java/hospital/simulation/` | Shared simulation engine, state, and events |
| `src/main/java/hospital/ui/` | JavaFX desktop dashboard |
| `src/main/resources/styles/` | Light and dark themes |
| `android/` | Native Android application |
| `scripts/` | Desktop packaging and shortcut scripts |
| `.github/workflows/` | Android CI and multi-platform release builds |
| `docs/screenshots/` | Dashboard preview used above |

## Build from source

### Desktop

Requirements: JDK 25 or newer and Maven 3.9 or newer.

```bash
mvn javafx:run
mvn clean verify
```

Package on the target operating system:

```bash
# Linux: set ERONIX_PACKAGE_TYPE to deb, rpm, or appimage as needed
./scripts/package-linux.sh

# Windows PowerShell
.\scripts\package-windows.ps1
```

### Android

Requirements: Android Studio, Android SDK Platform 35, build tools 35.0.0, and
JDK 17.

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to
`android/app/build/outputs/apk/debug/app-debug.apk`.

## Credits

ERONIX is an individual project by **Eng. Omar Abdelaziz**.

- Portfolio: [engomarabd-elaziz.github.io/My-Portofolio](https://engomarabd-elaziz.github.io/My-Portofolio/)
- Repository: [EngOmarAbd-Elaziz/ERONIX](https://github.com/EngOmarAbd-Elaziz/ERONIX)

---

<p align="center"><sub>Built to make operating-systems concepts visible, measurable, and a little more human.</sub></p>
