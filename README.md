# 🏪 MarketPOS

A secure, offline-first Point of Sale (POS) system for small and medium-sized retail stores. Built with Spring Boot and JavaFX as a standalone desktop application — no internet connection required for daily operations.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen?logo=springboot)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![H2](https://img.shields.io/badge/H2-AES--256%20Encrypted-red)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)

---

## ✨ Features

- **Sales & Checkout** — Barcode scanning, cart management, cash/card payments
- **Product Management** — Add, edit, delete products; bulk import via CSV/Excel
- **Employee Management** — ADMIN and KASIYER roles with separate permissions
- **Daily Reports** — Sales summaries, revenue breakdown by payment type
- **Automated Backups** — Daily SQL backups + Excel product list exports (Google Drive ready)
- **Backup & Restore** — One-click restore from any of the last 20 backups
- **License Management** — Per-market expiry dates with in-app renewal reminders
- **Auto-Update** — Detects and applies new releases automatically from GitHub
- **Dark / Light Theme** — User-selectable UI theme

---

## 🔒 Security

| Layer | Implementation |
|-------|---------------|
| Database encryption | H2 `CIPHER=AES` (AES-256) with per-installation key |
| Password hashing | BCrypt (cost factor 10) |
| API authentication | JWT (HMAC-SHA256), stateless, token blacklist on logout |
| Brute-force protection | Bucket4j rate limiting — 10 requests/min per IP |
| Multi-tenant isolation | Spring AOP aspect enforces market-scoped data access |
| Credential storage | Secrets in `AppData` only — never in source code or `application.properties` |
| Audit logging | All auth events, admin actions, and sales transactions are logged |
| Path traversal | Normalized path resolution with `startsWith` boundary check |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│               JavaFX UI Layer                │
│  GirisEkrani │ KasaEkrani │ YonetimEkrani   │
└────────────────────┬────────────────────────┘
                     │ HTTP (localhost)
┌────────────────────▼────────────────────────┐
│          Spring Boot REST API               │
│  LoginController │ SatisController          │
│  UrunController  │ KullaniciController      │
│  YedekController │ SuperAdminController     │
└────────────────────┬────────────────────────┘
                     │ JPA/Hibernate
┌────────────────────▼────────────────────────┐
│       H2 Embedded Database (AES-256)        │
│  AppData\Local\MarketPOS\veri.mv.db         │
└─────────────────────────────────────────────┘
```

**Role hierarchy:**

```
PATRON (developer)
  └── ADMIN (market owner)
        └── KASIYER (cashier)
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 3.3.4 |
| Frontend | JavaFX 21 |
| Database | H2 2.x (embedded, AES encrypted) |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security 6, JWT, Bucket4j |
| Excel export | Apache POI 5.2.5 |
| Build | Maven 3 |
| Packaging | jpackage (Windows EXE) |
| Runtime | JDK 21 (bundled) |

---

## 🚀 Getting Started

### Prerequisites

- JDK 21+
- Maven 3.8+

### Build

```bash
mvn package -DskipTests
```

The executable JAR is produced at `target/pos-0.0.1-SNAPSHOT.jar`.

### Run (development)

```bash
java -jar target/pos-0.0.1-SNAPSHOT.jar
```

On first launch the app creates `AppData\Local\MarketPOS\config.properties` with randomly generated secrets. A patron account is initialized with a default password stored as BCrypt in `patron.hash`.

### Packaged distribution

Use the provided `dist/` folder (built separately with `jpackage`) which bundles a JRE and produces a native Windows installer.

---

## 📁 Runtime Data (AppData)

All sensitive runtime data is stored outside the project directory:

```
%LOCALAPPDATA%\MarketPOS\
├── veri.mv.db          # AES-256 encrypted H2 database
├── config.properties   # JWT_SECRET, DB_KULLANICI_SIFRESI (auto-generated)
├── .dbkey              # AES file-encryption key (auto-generated)
├── patron.hash         # BCrypt hash of the patron password
├── yedek/
│   ├── gunluk/         # Daily SQL backups (ZIP)
│   ├── islem/          # Transaction-triggered backups (ZIP)
│   └── excel/          # Daily Excel product exports
└── guncelleme/         # Auto-update staging area
```

> None of these files are tracked by git.

---

## 🔑 Credential Overview

| Credential | Default | Where stored | Who knows it |
|-----------|---------|-------------|-------------|
| Patron password | `patron123` | `patron.hash` (BCrypt) | Developer only |
| Admin password | Set at registration | H2 DB (BCrypt) | Market owner |
| Cashier password | Set by admin | H2 DB (BCrypt) | Cashier |
| JWT secret | Auto-generated | `config.properties` | Nobody (auto) |
| DB user password | Auto-generated | `config.properties` | Nobody (auto) |
| DB AES key | Auto-generated | `.dbkey` | Nobody (auto) |

**Reset patron password:** delete `patron.hash` and restart — the app recreates it with the default.

---

## 📦 Release & Update

Releases are published on [GitHub Releases](../../releases). The app checks for updates on every launch and applies them automatically (Windows only).

To publish a new release:
1. Update `MEVCUT_SURUM` in `GuncellemeService.java`
2. Build: `mvn package -DskipTests`
3. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`
4. Create a GitHub Release for the tag and attach the JAR

---

## 📄 License

Proprietary — All rights reserved. © 2026 Mustafa Emirhan Özer
