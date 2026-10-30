# TTvpn™ — Enterprise-Grade WireGuard Client for Android

TTvpn is a custom-built, device-wide Android VPN client designed for the TreasureTrail ecosystem. Built from the ground up in Kotlin, it leverages the official WireGuard Go backend to deliver a secure, resilient, and zero-friction tunneling experience.

Unlike bloated commercial VPN apps, TTvpn is engineered for speed, transparency, and absolute reliability. It features a proprietary "Console" user interface, silent background updates, and a persistent lock mechanism that ensures your device never leaks traffic.

---

## 🚀 Core Features

- **True Device-Wide Tunneling:** Routes all IPv4 and IPv6 traffic (0.0.0.0/0) through the secure tunnel, ensuring no data leaks outside the VPN.
- **"Lock VPN" (Kill Switch & Auto-Heal):** A persistent state mode that enforces the connection. If the network drops, switches from Wi-Fi to cellular, or the device reboots, the tunnel automatically re-establishes itself.
- **Silent Background Updater:** No app stores required. The app checks GitHub Releases in the background, downloads new versions silently over unmetered networks, and prompts for a one-tap system install.
- **Network-Aware State Machine:** Real-time UI updates reflecting exactly what the tunnel is doing (SECURED, STANDING BY, CONNECTING, DISCONNECTED), including network type detection (Wi-Fi, Cellular, Ethernet).
- **Custom DNS Override:** Power users can long-press the session panel to inject a custom DNS server (e.g., 9.9.9.9 or NextDNS IPs), bypassing the default provider.
- **Built-in Console Event Log:** A real-time, in-app diagnostic log that tracks connection state changes, network transitions, and app events with millisecond precision.
- **Voucher-Based Provisioning:** Secure, API-driven peer registration using single-use vouchers, ensuring only authorized users can generate WireGuard keypairs.

---

## 🛡️ The Engine: WireGuard® Go

TTvpn is powered by the official WireGuard Go Backend (`com.wireguard.android:tunnel`), specifically implementing `GoBackend.VpnService`.

**Why WireGuard Go?**
- **State-of-the-Art Cryptography:** Uses the Noise protocol framework, Curve25519, ChaCha20, Poly1305, BLAKE2s, and SipHash24. It is vastly more secure and faster than legacy protocols like OpenVPN or IPSec.
- **Battery Efficiency:** WireGuard is designed to be incredibly lightweight. It uses minimal CPU cycles, extending battery life compared to traditional VPNs.
- **Userspace Reliability:** By using the Go implementation on Android, TTvpn avoids kernel-level fragmentation issues across different Android manufacturers, ensuring stable operation on everything from Samsung to Google Pixel devices.
- **Seamless Roaming:** WireGuard handles network changes (like moving from Wi-Fi to 4G) natively without dropping the cryptographic session.

---

## 🏗️ Build & Architecture

TTvpn is built using modern Android development standards, completely free of bloatware, trackers, or ad SDKs.

- **Language:** 100% Kotlin (View System, no Compose for maximum stability and small APK size).
- **Target SDK:** 34 (Android 14) / **Minimum SDK:** 23 (Android 6.0).
- **Architecture:** Foreground Service (`VpnService`) bound to a custom `MainActivity` state machine.
- **Multi-ABI Support:** Compiles native WireGuard binaries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

### 🔄 The CI/CD Pipeline (GitHub Actions)

We do not build locally. Every release is compiled, signed, and published automatically by GitHub Actions:

1. Pushes to the `main` branch trigger a build.
2. The workflow decodes a secure Base64 Keystore.
3. Gradle compiles the release APK.
4. The workflow generates a `version.json` file.
5. The APK and JSON are published to a GitHub Release.
6. The app's internal updater reads that JSON to trigger silent updates.

---

## 🎨 The "Console" UI

TTvpn rejects the oversized, bubbly design language of modern consumer apps in favor of a dense, information-rich "Console" aesthetic.

- **Typography:** Monospace fonts for all technical data (IP addresses, durations, data usage).
- **Information Density:** Everything you need to know on one screen—Endpoint, Duration, Data Rx/Tx, and Entitlement—without scrolling.
- **State Clarity:** Color-coded status dots (Green = Secured, Amber = Standing By, Red = Disconnected) give you instant visual feedback.
- **No Fluff:** No world maps, no fake speed tests, no ads. Just a professional dashboard for your connection.

---

## 🔒 Security & Privacy Posture

- **Zero Telemetry:** The app contains no analytics, crash reporting, or tracking SDKs. Your data usage and logs remain entirely on your device.
- **No Logs Policy:** The server stores only the necessary cryptographic public keys and assigned IPs to maintain the connection. No browsing history is ever captured or stored.
- **Foreground Service Protection:** The VPN runs as a strict Android Foreground Service with a persistent notification, ensuring the OS never kills the tunnel to free up memory.

---

## 🛠️ Installation

Since TTvpn is distributed directly to TreasureTrail users, it is sideloaded via our secure release pipeline.

### First Time Install:
1. Download the latest `TTVPN.apk` from the Releases page.
2. Allow "Install from Unknown Sources" in your Android settings.
3. Install and open the app.
4. Enter your activation voucher provided by the TreasureTrail platform.

### Future Updates:
You don't need to do anything. The app will silently download the update in the background on Wi-Fi and prompt you with an "Update Ready" button in the footer. One tap, and it installs over the top.

---

*TTvpn™ is a product of PayBridge (Pty) Ltd. WireGuard® is a registered trademark of Jason A. Donenfeld.*
