<div align="center">
  <img src="src/main/resources/icons/app-icon.png" alt="ERONIX emergency room cross icon" width="136" height="136">

  <h1>ERONIX</h1>

  <p><strong>A live emergency department simulation for desktop and Android.</strong></p>

  <p>
    Follow patients from the waiting room to treatment with a real, concurrent
    simulation engine—not a presentation-only animation.
  </p>

  <p>
    <a href="https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/latest"><strong>Get ERONIX</strong></a>
    &nbsp;·&nbsp;
    <a href="#preview">See the dashboard</a>
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

## Preview

<p align="center">
  <img src="docs/screenshots/dashboard-preview.svg" alt="Illustrative ERONIX desktop dashboard preview" width="100%">
</p>

<p align="center"><sub>Illustrative preview based on the real dashboard layout. Simulation data shown here is sample UI content.</sub></p>

## A real-time simulation, at a glance

ERONIX is a standalone JavaFX emergency-room simulation and a native Android
companion app. Both interfaces are powered by the same Java simulation engine.
The dashboard stays connected to the running simulation as patients arrive,
wait for a bed, receive treatment, and leave.

| Follow the flow | Manage the department | Understand the concurrency |
|---|---|---|
| Watch waiting-room occupancy, patients outside, treatment progress, and completed cases update live. | Configure patient volume, waiting-room capacity, doctors, treatment rooms, and arrival/treatment times. | Explore Java threads, bounded buffers, custom semaphores, and coordinated resource sharing. |

### Designed to be easy to follow

- **Live dashboard:** patient statistics, waiting-room seats, doctor and room
  states, and a chronological event timeline.
- **Adaptive Android app:** five focused pages—Home, Waiting, Team, Events,
  and Settings—with portrait and landscape layouts.
- **Light and dark themes:** switch the desktop theme while a simulation runs.
- **One shared engine:** desktop and Android screens report the same simulation
  state; the UI does not create its own fake activity.
- **Ready-to-run releases:** desktop packages bundle a Java runtime. No Java,
  Maven, or JavaFX installation is needed to launch them.

## Downloads

Get the latest release and choose the package for your device:

| Platform | Download | Notes |
|---|---|---|
| **Windows** | [ERONIX-windows.msi](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/latest/download/ERONIX-windows.msi) | Run the installer; later MSI releases upgrade the installed application. |
| **Linux x86_64** | [ERONIX-linux.deb](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/latest/download/ERONIX-linux.deb) | Install the Debian package; later package releases upgrade it. |
| **Android** | [ERONIX-android.apk](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/latest/download/ERONIX-android.apk) | Android 14 (API 34) or newer. Allow installation from your selected source. |

Use **Check for updates** inside the application to open the latest release when
one is available. Windows and Linux installers use stable application/package
identities so installing a newer release upgrades the existing installation.
Android checks use the same release page; Android asks the user to confirm APK
installation. Since older desktop releases were portable archives, their users
must install the first MSI/DEB release once and may then remove the extracted
old copy. Older Android releases also require a one-time reinstall because they
were not signed with a persistent key.

The Android release workflow requires these GitHub Actions secrets:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
and `ANDROID_KEY_PASSWORD`. The keystore is used to sign every release APK and
must be retained securely; replacing it prevents Android from applying updates
over an already installed release. To create a new key, use `keytool -genkeypair
-v -keystore eronix-release.jks -alias eronix -keyalg RSA -keysize 2048
-validity 10000`, then store the keystore and its credentials securely and
configure the four secrets in the repository's Actions settings. Never commit
the keystore or passwords. Until these secrets are configured, a release still
publishes its Windows and Linux installers, while the Android APK is skipped.
Users of earlier APKs must uninstall and install the first permanently signed
release once. Visit
[all releases](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases) for version
history and release notes.

## Build from source

### Desktop application

**Requirements:** JDK 25 or newer and Maven 3.9 or newer.

```bash
# Run the JavaFX application
mvn javafx:run

# Run tests and verify the project
mvn clean verify
```

To package a self-contained application image, build on the target operating
system. `jpackage` uses the JDK installed on that machine.

```bash
# Linux
./scripts/package-linux.sh

# Windows PowerShell
.\scripts\package-windows.ps1
```

The Debian package is written to `dist/linux/packages/`; the Windows MSI is
written to `dist/windows/installers/`. Set `ERONIX_VERSION` to a numeric dotted
version (for example `1.2.0`) when building a release installer. On Fedora-like
systems, set `ERONIX_PACKAGE_TYPE=rpm` to build an RPM instead of the default
Debian package.

### Android application

**Requirements:** Android Studio, Android SDK Platform 35, build tools 35.0.0,
and JDK 17.

Open the `android/` directory in Android Studio, or build and test from a
terminal:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to
`android/app/build/outputs/apk/debug/app-debug.apk`.

## What the simulation demonstrates

- Each patient and doctor runs on its own Java thread.
- The waiting room is a bounded buffer synchronized by custom `empty`, `full`,
  and `mutex` semaphores. Patients wait outside when it is full; doctors block
  when it is empty.
- Treatment rooms are a separate shared resource managed by a custom counting
  semaphore. If a room is unavailable, the patient returns to the waiting room.
- Patients wait for treatment outcomes through notification rather than
  busy-waiting. The coordinator joins worker threads before reporting
  completion.
- Simulation callbacks update JavaFX on the JavaFX Application Thread.

The interface supports configurable waiting-room capacity (1–30), doctors
(1–20), patients (1–200), and treatment rooms (1–20). Treatment times are
constrained to 1–5 seconds.

## Project layout

| Path | Purpose |
|---|---|
| `src/main/java/hospital/simulation/` | Shared simulation engine, state, and events |
| `src/main/java/hospital/ui/` | JavaFX desktop dashboard |
| `src/main/resources/styles/` | Light and dark themes |
| `src/main/resources/icons/` | Application icon |
| `android/` | Native Android app and Gradle wrapper |
| `scripts/` | Native desktop packaging and shortcut scripts |
| `.github/workflows/` | Android CI and tagged multi-platform release builds |
| `docs/screenshots/` | README dashboard preview |

## License and credits

ERONIX is an individual project by [Eng. Omar Abdelaziz](https://engomarabd-elaziz.github.io/My-Portofolio/).
The desktop and Android apps link to the project owner's portfolio.

---

<p align="center"><sub>Built to make operating systems concepts visible, measurable, and a little more human.</sub></p>
