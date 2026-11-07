<div align="center">

  <img src="src/main/resources/icons/app-icon.png" alt="ERONIX App Icon" width="100" height="100">

  # 🏥 ERONIX
  ### Real-Time Emergency Department Simulation Engine

  <p align="center">
    <img src="https://img.shields.io/badge/release-v1.0.3-0052CC?style=for-the-badge&logo=github&logoColor=white" alt="Version 1.0.3" />
    <img src="https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20Android-4E5D6C?style=for-the-badge" alt="Platforms" />
    <img src="https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25" />
    <img src="https://img.shields.io/badge/JavaFX-Desktop-007396?style=for-the-badge&logo=java&logoColor=white" alt="JavaFX" />
    <img src="https://img.shields.io/badge/Android-Mobile-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  </p>

  <p><strong>A robust, cross-platform simulation system bringing concurrent operating system concepts and emergency department workflows to life.</strong></p>
</div>

---

## 🎯 Overview

**ERONIX** is an advanced simulation application engineered to model the dynamic, high-pressure environment of a hospital emergency department. Built with a unified, high-performance Java core engine, it bridges theory and practice by simulating real-time triage, waiting room capacities, doctor allocation, and treatment room bottlenecks.

Whether you are exploring multi-threaded synchronization or observing how patient queues shift under different loads, ERONIX offers a clean, responsive graphical experience across both desktop and mobile devices.

> **Disclaimer:** ERONIX is an educational and architectural demonstration tool. It is not intended for clinical deployment or medical decision-making.

---

<div align="center">

<a id="-live-preview"></a>
## 🖥️ Application Preview

</div>

<p align="center">
  <img src="docs/screenshots/dashboard-preview.svg" alt="ERONIX Dashboard Screenshot" width="100%">
</p>

<p align="center"><sub>The desktop control center featuring live telemetry, capacity tracking, and chronological event logging.</sub></p>

---

## ✨ Core Highlights

- **Unified Simulation Engine:** A shared Java core handles all concurrency, state updates, and resource management uniformly across platforms.
- **Cross-Platform Delivery:** Native packages built specifically for Windows (`.msi`), Linux (`.deb` / `.rpm`), and Android (`.apk`).
- **Concurrency in Action:** Directly visualizes critical OS concepts like semaphores, mutexes, bounded buffers, and thread lifecycles.
- **Adaptive Interface Themes:** Fully custom Light and Dark mode stylesheets built for readability during long monitoring sessions.
- **Comprehensive Mobile Control:** The Android application neatly sections functionality into dedicated Home, Waiting, Team, Events, and Settings panels.

---

## 📦 Downloads (v1.0.3 Releases)

Select the package corresponding to your operating system:

| Platform | Format | Action Link |
| :--- | :--- | :--- |
| **Windows** | MSI Installer | [Download ERONIX.msi](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.msi) |
| **Fedora / RHEL** | RPM Package | [Download ERONIX.rpm](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.rpm) |
| **Ubuntu / Debian** | DEB Package | [Download ERONIX.deb](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.deb) |
| **Android (14+)** | APK Package | [Download ERONIX.apk](https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/download/v1.0.3/ERONIX.apk) |

---

## ⚙️ Under the Hood: How It Works

ERONIX translates theoretical concurrency problems into a tangible, observable workflow:

1. **Patient Generation:** Incoming patients spawn as individual worker threads according to your customized arrival frequency.
2. **Bounded Buffer Queuing:** The waiting room functions as a synchronized bounded buffer. Custom `empty`, `full`, and `mutex` semaphores coordinate patient entry and doctor selection safely.
3. **Staff Coordination:** Active doctor threads evaluate the queue and pull patients forward, instantly requesting an available treatment space.
4. **Shared Resource Allocation:** Counting semaphores govern access to limited treatment rooms. Once care concludes, the room unlocks for the next queue member.
5. **Real-Time Telemetry:** The engine streams state snapshots and chronological activity events straight to the UI dashboard without artificial animation hacks.

---

## 📐 Architecture & Technology Stack

| Layer | Technologies & Tooling |
| :--- | :--- |
| **Core Logic** | Java (Shared Simulation Engine) |
| **Desktop Client** | Java 25, JavaFX, Maven |
| **Mobile Client** | Java, Android SDK, Gradle |
| **Styling & Assets** | Custom CSS Themes, SVG Vectors, PNG Icons |

---

<div align="center">

## 👨‍💻 Creator & Developer

Designed, engineered, and maintained by **Eng. Omar Abdelaziz**

<p>
  <a href="https://engomarabd-elaziz.github.io/My-Portofolio/">🌐 Portfolio</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/EngOmarAbd-Elaziz">💻 GitHub</a>
</p>

<br>

<sub>Crafted with passion to make concurrent systems visible, interactive, and impactful.</sub>

</div>
