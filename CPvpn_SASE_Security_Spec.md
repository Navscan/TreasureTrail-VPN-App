# CPvpn SASE Architecture & Security Specification

**Document Version:** 1.0  
**Date:** November 2026  
**Classification:** Internal / B2B Sales Enablement  
**Author:** PayBridge(Pty) Ltd

---

## 1. Executive Summary

CPvpn is not merely a Virtual Private Network (VPN); it is a lightweight **SASE (Secure Access Service Edge)** platform. It combines network security functions (like firewall-as-a-service, zero-trust network access, and threat intelligence) with WAN capabilities to support the secure digital transformation of enterprise clients.

The architecture relies on three foundational pillars: **Nginx (Ingress Protection), Fail2Ban (Active IPS), and WireGuard/SELinux (Secure Transport & Kernel Enforcement).**

---

## 2. Infrastructure Baseline

The platform is hosted on an enterprise-grade Linux environment, hardened at the kernel and network levels.

*   **Operating System:** AlmaLinux 10.2 (Lavender Lion)
*   **Kernel Security:** SELinux is set to **Enforcing** mode. Even if an attacker compromises the Node.js application or Nginx, the kernel will prevent them from accessing root files, tampering with the WireGuard config, or spawning malicious processes.
*   **Firewall (firewalld):** Configured with strict default-deny ingress rules.
    *   **Allowed Ports:** 80/tcp (HTTP redirect), 443/tcp (HTTPS), 22/tcp (SSH), 51820/udp (WireGuard).
    *   **Masquerading:** Enabled (`masquerade: yes`) to allow seamless NAT routing for VPN clients without leaking their physical IPs.
    *   **Forwarding:** Enabled (`forward: yes`) to route traffic securely through the VPS.

---

## 3. Pillar 1: Nginx (The Secure Edge)

Nginx acts as the reverse proxy, handling all incoming traffic and ensuring that only cryptographically secure connections reach the CPvpn SaaS backend.

*   **Cryptography:** Enforces TLS 1.2 and TLS 1.3 exclusively, utilizing high-grade cipher suites (ECDHE-RSA-AES256-GCM-SHA384, CHACHA20-POLY1305) to prevent downgrade and man-in-the-middle (MitM) attacks.
*   **Hardened HTTP Headers:** 
    *   `Strict-Transport-Security` (HSTS): Forces browsers to use HTTPS only, preventing SSL-stripping. Preload enabled.
    *   `X-Frame-Options: SAMEORIGIN`: Prevents clickjacking attacks.
    *   `X-Content-Type-Options: nosniff`: Blocks MIME-type sniffing vulnerabilities.
    *   `Referrer-Policy`: Restricts cross-origin data leakage.

---

## 4. Pillar 2: Fail2Ban & Cerberus Protocol (Active IPS)

Fail2Ban provides Intrusion Prevention System (IPS) capabilities. On CPvpn, we have enhanced it into the **Cerberus Protocol**—an active defense and threat intelligence engine.

*   **Zero-Trust Access:** The SSH port and web portals are protected by a strict **1-hour ban** after **5 failed attempts** within a 10-minute window.
*   **Active Jails:**
    *   `sshd`: Protects the root server from brute-force SSH attacks.
    *   `nginx-botsearch`: Automatically bans vulnerability scanners and malicious scrapers probing the web server.
    *   `nginx-req-limit`: Automatically bans IPs attempting DDoS request floods.
*   **Cerberus Enrichment:** When an IP is banned, the CPvpn backend asynchronously enriches the IP with Geolocation and ISP data (e.g., detecting traffic from bulletproof hosting in Russia or data centers in China) and pushes it to the Enterprise Threat Centre in real-time.

---

## 5. Pillar 3: WireGuard (The Secure Transport)

The actual data plane is handled by WireGuard, a modern, extremely fast, and secure VPN protocol.

*   **Cryptokey Routing:** WireGuard uses state-of-the-art cryptography (Curve25519 for key exchange, ChaCha20 for encryption, Poly1305 for authentication).
*   **Server Public Key:** `8bL+3Y2M1uaERIWMp/g2+Xk7246cvJX22qKNzSiejHQ=`
*   **Protocol Efficiency:** WireGuard operates at the kernel level (via `wg0`), making it significantly faster and more battery-efficient than legacy VPNs like OpenVPN or IPsec.
*   **Peer Isolation:** Every user or company employee gets a dedicated WireGuard peer with a unique IP from the `10.0.0.0/24` pool.

---

## 6. B2B Value Proposition

1.  **No Shared Subnets:** Zero-trust architecture ensures each Enterprise voucher provision creates a distinct, isolated tunnel.
2.  **Private Threat Intelligence:** Because we control the Nginx and Fail2Ban layer, Enterprise clients get a live feed of exactly who is attacking the network, transformed into actionable intelligence in the CPvpn Threat Centre.
3.  **SaaS & Voucher Ready:** The backend handles billing (Paystack), user provisioning (JWT), and prepaid 1-year Enterprise licenses (SASE vouchers) via the FlipStack portal. No recurring credit card needed for corporate rollouts.

---
*End of Document*
