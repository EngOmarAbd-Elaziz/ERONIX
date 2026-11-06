# ERONIX — Emergency Room Simulation

ERONIX is a standalone, GUI-first JavaFX application for simulating patient flow
in a hospital emergency department. It was developed as an individual project
and demonstrates Java threads, a custom semaphore, and the bounded-buffer
Producer–Consumer pattern.

The application displays `© 2026 Eng. Omar Abdelaziz` in its footer. The name
is a clickable link to the [project owner's portfolio](https://engomarabd-elaziz.github.io/My-Portofolio/)
in both the desktop and Android interfaces.

The dashboard visualizes the actual simulation state; it does not run a
separate presentation-only simulation. The interface is English-only. Both
themes, the waiting room, doctors, treatment rooms, and
chronological event timeline update from the same simulation engine.

## Run a ready-to-use application

Platform-specific application images include a Java runtime and the required
JavaFX libraries. End users do not need to install Java, Maven, or JavaFX.

- **Windows:** Download and extract the `ERONIX-windows` artifact. Run
  `powershell -ExecutionPolicy Bypass -File .\create-desktop-shortcut.ps1` once
  to place an ERONIX shortcut on your desktop, or launch `ERONIX/ERONIX.exe`
  directly.
- **Linux:** Download and extract the `ERONIX-linux` artifact. Run
  `./create-desktop-shortcut.sh` once to place an ERONIX shortcut on your
  desktop, or launch `ERONIX/bin/ERONIX` directly.

The application image is built for the operating system and CPU architecture
of its build machine. The included GitHub Actions workflow builds Windows and
Linux application images plus an Android APK when manually dispatched or when
a version tag is pushed. Pushing a version tag such as `v1.0.0` publishes one
GitHub release containing the three platform downloads.

Ready-to-use builds for this machine are collected outside the source tree at
`~/Downloads/ERONIX-Executables/`:

- `linux/ERONIX/` contains the Linux application image.
- `android/ERONIX-debug.apk` contains the Android install package.
- `windows/` is reserved for the Windows application image. A native Windows
  build must be produced on Windows by running `scripts/package-windows.ps1`;
  `jpackage` cannot cross-build a Windows launcher from Linux.

## Android app

The native Android app reuses the same Java simulation engine and presents an
adaptive ERONIX dashboard. It supports Android 14 (API 34) and newer. Open the
`android/` directory in Android Studio, allow Gradle sync to finish, then run
the `app` configuration on an emulator or connected device.

The Android dashboard is split into five bottom-navigation pages: **Home**
for live statistics and completion status, **Waiting** for waiting-room
occupancy and patients outside, **Team** for doctors and treatment rooms,
**Events** for the chronological timeline, and **Settings** for simulation
configuration and controls. Switching pages does not restart or pause the
simulation.

To build a debug APK from a machine with Android SDK and JDK 17 installed:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to
`android/app/build/outputs/apk/debug/app-debug.apk`. For this machine, the
ready-to-install debug APK is also staged at
`~/Downloads/ERONIX-Executables/android/ERONIX-debug.apk`. The Android UI
adapts its configuration and statistics grids, waiting-room seats, doctor
cards, and dashboard width to the available screen width. It supports portrait
and landscape rotation, keeps the configuration and live simulation state when
the screen size changes, and remains vertically scrollable on compact screens.

The Android project includes the Gradle 8.9 wrapper and uses Java 17, Android
Gradle Plugin 8.7.3, compile SDK 35, and minimum SDK 34. Install Android SDK
Platform 35 and the corresponding build tools through Android Studio's SDK
Manager before building.

## Build an application image locally

Build only for the operating system on which you are running the build. Install
a JDK 21 or newer, Maven 3.9 or newer, and the platform's `jpackage` utility
(included with the JDK).

### Linux

```bash
./scripts/package-linux.sh
```

The ready-to-run application image is created at `dist/linux/ERONIX/`.

### Windows PowerShell

```powershell
.\scripts\package-windows.ps1
```

The ready-to-run application image is created at `dist\windows\ERONIX\`.
Launch `ERONIX.exe` inside that directory.

## Run from source

For development, install JDK 25 or newer and Maven 3.9 or newer, then run:

```bash
mvn javafx:run
```

Run the test suite with:

```bash
mvn clean verify
```

The JavaFX dashboard opens full-screen. Press `Esc` or `F11` to leave or toggle full-screen mode. Use the sun/moon
button to change the theme. The interface is English-only.

## Using the dashboard

Before starting, configure the waiting-room capacity, number of doctors,
patients, treatment rooms, and the minimum/maximum arrival and treatment times.
Time values are entered in seconds, with up to three decimal places. Treatment
duration is constrained to 1–5 seconds to remain compatible with the
assignment. The default treatment-room count is two; the configurable range is
1–20.

Select **Start simulation** and follow the live statistics, waiting-room seats,
doctor cards, treatment-room cards, and event timeline. Stop, reset, and run a
new simulation from the controls. The theme can be changed while the simulation
is running without resetting its state.

## Simulation design

- Each patient and doctor runs on an independent Java thread.
- The waiting room is a bounded buffer synchronized by the project's custom
  `empty`, `full`, and `mutex` semaphores. Patients wait outside when it is full;
  doctors block when it is empty.
- Treatment rooms are a separate shared resource managed by the custom
  counting semaphore. Each doctor waits up to three seconds to acquire a room;
  if none becomes available, the patient returns to the waiting room.
- Patients wait for treatment outcomes through notification rather than
  busy-waiting. The coordinator waits for doctors to be ready and joins worker
  threads before reporting completion.
- Simulation callbacks update JavaFX only on the JavaFX Application Thread.
- Console execution remains available for the assignment:

  ```bash
  mvn -q -DskipTests package
  java -cp target/classes hospital.simulation.Hospital
  ```

## Project map

| Path | Responsibility |
|---|---|
| `src/main/java/hospital/simulation/` | Simulation threads, custom semaphores, state, and event reporting |
| `src/main/java/hospital/ui/HospitalApp.java` | JavaFX dashboard, controls, themes, and localization |
| `src/main/java/hospital/ui/DashboardText.java` | English interface and event text |
| `src/main/resources/icons/app-icon.png` | Embedded application and dashboard icon |
| `ERONIX.ico` | Windows application-image icon |
| `src/main/resources/styles/` | Light and dark themes |
| `scripts/` | Platform-specific `jpackage` build scripts |
| `.github/workflows/package.yml` | Automated Windows and Linux application-image builds |
| `android/` | Native Android app, reusing the shared simulation engine |

## Assignment hand-in

This is configured as an individual, self-contained application. Before
submitting it for a course, follow the instructor's packaging and naming rules;
course-specific identifiers and submission requirements are not assumed here.
