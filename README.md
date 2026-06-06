# 🔄 Web Refresher Pro

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Web Refresher Pro** is a lightweight, robust Android utility designed to automate web page refreshing at customized intervals. Built specifically to tackle session timeouts on demanding enterprise web portals, it ensures you stay active and online on platforms like **Microsoft Teams Web**—even if you clear the app from your system's recent apps overview.

---

## 🚀 Key Features

* **⚡ Persistent Background Engine:** Runs flawlessly in the background. The automation persists even after force-closing or swiping the app away from the recent apps tray.
* **⚙️ Custom Automation Profiles:** Fine-tune your refresh frequency with preset intervals (`1m`, `3m`, `5m`) or define a fully customized time window.
* **⏱️ Smart Expiry Timer:** Prevent infinite loops by configuring a hard stop time (e.g., auto-terminate automation after 60 minutes).
* **🔒 Privacy & Security Focused:** No unnecessary intrusive permissions. The app only requests optional notification access and standard battery optimization exemptions. No external data tracking.
* **🛡️ Safety Mode:** Injects an optional, organic `1–30 second random delay` to each refresh cycle to mimic natural human interactions and prevent bot-detection flags.
* **🖥️ Desktop Mode Toggle:** Force render desktop layouts directly on your mobile device for complex dashboards that lack native responsive mobile layouts.
* **📊 Live Notification Control:** Track exactly when the next refresh will occur and how much total automation time is remaining directly from your system tray.

---

## 📱 User Interface & Workflow

### 1. Initial Setup & Destination Selection
When launched, input your target URL (e.g., `teams.cloud.microsoft`) into the address portal. 
> 💡 *Note: The app remembers your history and populates your landing dash with a **Recents** index for single-tap access.*

### 2. Authentication & Navigation
Log into your enterprise account securely via the integrated web view interface. Once authorized, a configuration floating action button (**Cog Icon ⚙️**) will anchor itself to the bottom right of the viewport.

### 3. Automation Engine Activation
Tap the **Cog Icon** to pull up the **Automation Settings** panel:
* Set your **Interval** and **Stop After** durations.
* Toggle **Safety Mode** or **Desktop Mode** to match portal requirements.
* Tap **Start Automation**.

### 4. Background Monitoring
Once engaged, a persistent notification keeping tab on execution states will launch. You can now dismiss the application safely from your background history.

---

## 🛠️ Permissions Handled Elegantly

To ensure uninterrupted service delivery while respecting system resources, the app requests:
1.  **Notification Access (Optional):** Used exclusively to render live lifecycle status indicators and provide an immediate interface-wide "Stop" button.
2.  **Battery Optimization Bypass:** Essential for preventing the Android OS Doze architecture from putting the network engine to sleep when background tracking is running.

---

## 🔧 Installation & Deployment

### Prerequisites
* Android 8.0 (API Level 26) or higher.

### Manual Installation
1. Navigate to the [Releases](https://github.com/YOUR_USERNAME/Web-Refresher-Pro/releases) page of this repository.
2. Download the latest compiled production alignment `.apk` payload file.
3. Open the file on your target Android hardware architecture and grant permissions to install from unknown sources if prompted.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---

*Designed with 💙 by Bunny.*
