# 🛡️ CPvpn Enterprise: The SASE Solution for Modern Businesses

**Confidential Pitch Deck**  
**Presented by:** PayBridge (Pty) Ltd  
**Product:** CPvpn Enterprise  
**Date:** November 2026

---

## Slide 1: The Problem
**Consumer VPNs are not built for business.**

*   **Zero Visibility:** Traditional VPNs give you a connection, but no insight into who is attacking your network or where your traffic is going.
*   **Billing Sprawl:** Managing 50 separate monthly credit card subscriptions for employees is an accounting nightmare.
*   **Data Leakage:** Consumer VPNs often log user data, creating massive compliance and privacy risks for corporate environments.
*   **Weak Security:** Many legacy VPNs use outdated protocols like OpenVPN or PPTP that are slow and vulnerable to modern attacks.

---

## Slide 2: The Solution
**CPvpn Enterprise (SASE).** 

We provide a lightweight Secure Access Service Edge platform designed specifically for businesses, ISPs, and remote teams.

*   **Pre-Paid Licensing:** Issue 1-Year License Keys to employees via secure vouchers. No recurring credit cards. Invoice once, deploy instantly.
*   **Military-Grade Speed:** Powered by WireGuard, the most modern and fastest encryption protocol available.
*   **Zero-Trust Architecture:** Every employee gets a dedicated, isolated tunnel with their own IP address.
*   **Bring Your Own Device (BYOD) Ready:** Simple Android app deployment.

---

## Slide 3: The "Cerberus" Advantage
**Enterprise-grade threat intelligence, right in the employee's pocket.**

Unlike standard VPNs, CPvpn is integrated with the **Cerberus Protocol**.
*   **Live Threat Intel:** Employees can see real-time Fail2ban logs, banned IP addresses, and GeoIP tracking of attackers directly inside the app.
*   **Server Vitals:** Monitor RAM, Disk usage, and Uptime in real-time.
*   **Active IPS:** Automated Intrusion Prevention System that bans malicious IPs across SSH and web interfaces instantly.

*(Insert screenshot of the Threat Intel tab here)*

---

## Slide 4: Architecture & Security
**Hardened at the Kernel Level.**

*   **OS:** AlmaLinux (RHEL-based) with SELinux enforcing.
*   **Edge Protection:** Nginx reverse proxy with TLS 1.3, HSTS, and strict security headers.
*   **Firewall:** Strict `firewalld` default-deny ingress rules. 
*   **Tunnel:** Dedicated WireGuard interface with strict MTU (1420) to prevent fragmentation attacks.

---

## Slide 5: Pricing & ROI

**Cost-Effective, Transparent, and Easy to Budget.**

| Tier | Deployment | Billing | Best For |
| :--- | :--- | :--- | :--- |
| **Pro (B2C)** | Consumer App | R99/month | Individual users |
| **Enterprise (B2B)** | Custom VPS/App | **Pre-paid 1-Year Key** | Businesses & Teams |

*   **No Hidden Fees:** The voucher covers the full year of access to the dedicated infrastructure.
*   **Scalable:** Deploy a new VPS in Europe or the US in minutes using our `cerberus-install.sh` script and route Enterprise clients there.

---

## Slide 6: Next Steps (How to Deploy)

1.  **Generate Keys:** Log in to the FlipStack Enterprise Portal to generate your batch of 1-Year Vouchers.
2.  **Distribute:** Send the voucher codes to your employees.
3.  **Install:** Employees download the CPvpn APK from FlipStack.store.
4.  **Activate:** Employees log in with their corporate email, paste the voucher, and connect.

**Ready to secure your remote workforce?**  
Contact: [Your Email Address]  
Visit: https://flipstack.store/cpvpn-enterprise.html
