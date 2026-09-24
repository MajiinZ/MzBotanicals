🌱 MzBotanicals — Kotlin Multiplatform E-Commerce App

Backend setup, API contract, security tests and rollout instructions: [server/README.md](server/README.md).

MzBotanicals is a secure, modern e-commerce platform built using Kotlin Multiplatform (KMP). Designed for the sale of premium genetics—including specialized seeds and branded merchandise—this cross-platform project emphasizes performance, privacy, and a seamless shopping experience.

⚖️ Legal Disclaimer
MzBotanicals does not and is not intended to facilitate the sale of cannabis flower, concentrates, or other regulated cannabis products.
In accordance with California state law and local ordinances, this platform is limited to the lawful distribution of immature cannabis seeds and branded merchandise only.
All use of this platform must comply with applicable laws, and no functionality is provided for the sale, purchase, or distribution of cannabis flower.

Built with Kotlin's robust cross-platform capabilities, MzBotanicals targets:

✅ Android (Jetpack Compose + Material 3 Design Practices)
✅ Desktop (Compose Multiplatform)
✅ Web (WASM, in progress)
✅ Server (Ktor backend with Firebase integration)

📽️ Demo
Check out the app in action on YouTube Shorts:

Watch the demo

📦 Project Structure

composeApp/
UI and business logic for Android and Desktop.
Built with JetBrains Compose Multiplatform, leveraging Material 3 design guidelines on Android.
Uses shared view models and design components.

shared/
Core logic, models, networking, and utilities.
Most development takes place in commonMain/.
Platform-specific extensions (e.g., androidMain/, desktopMain/) override shared behavior when necessary.

server/
Ktor-based backend with integration for:

Firebase Authentication
Server-validated profiles, carts and catalog management
Server-priced quotes and idempotent order drafts
The server is an independent Ktor/Firebase build. Payments are deliberately disabled until provider onboarding and server-side payment verification are implemented.

🔐 Key Features

Secure Firebase Authentication
Supports Google Sign-In for streamlined user access.
Real-Time Catalog Updates
Claim-authorized admins manage products through the validated backend API.
Order Drafts
The backend calculates draft subtotals from catalog prices. Payments, inventory reservations and order notifications are not enabled.
Shared Codebase
UI and logic are shared across Android and Desktop for consistency and maintainability.
Modular Architecture
Project structure follows clean architecture principles, separating concerns across modules like feature/, data/, navigation/, and di/.
Modern Android UI
Android app implements Jetpack Compose with Material 3 design principles, ensuring a consistent, intuitive, and responsive user interface.
🚀 Vision

MzBotanicals is committed to delivering a trusted, discreet, and cross-platform shopping experience tailored for customers in the premium genetics market. The app is built with security-first practices and optimized for reliability on both mobile and desktop environments.

🛠️ Tech Stack

Kotlin Multiplatform (KMP)
JetBrains Compose Multiplatform
Jetpack Compose + Material 3 (Android UI)
Ktor (Backend APIs)
Firebase (Authentication, Realtime DB, Storage)
Google Sign-In
Modular, multi-layer architecture (inspired by clean architecture practices)
🧪 In Progress

Development is active. Planned upcoming features include:

🔄 Payment Gateway Integration (Stripe, Coinbase Commerce)
📦 Order Tracking System
📱 iOS Support using Swift + Kotlin Multiplatform
🧠 Advanced Filtering & Search
📊 Admin Analytics Dashboard
🧾 Printable Invoices & Receipts
🙌 Contributions

This is a private, branded project and not accepting outside contributions at this time.
However, you are welcome to explore or fork the project for educational purposes only.

📄 License

Private Use Only
All rights reserved to MzBotanicals.
Commercial redistribution or unauthorized resale is strictly prohibited.

⚖️ Extended Compliance Notice

MzBotanicals operates strictly within the scope of California cannabis law and local ordinances. This application is intended only for the lawful sale of immature cannabis seeds and branded merchandise.

🚫 No Flower Sales: The platform does not support, permit, or encourage the sale of cannabis flower, concentrates, or other adult-use cannabis products.
🔞 Age Restriction: All customers must be 21 years of age or older, and proper ID verification is required under applicable law.
🌎 Jurisdictional Limits: Cannabis-related products may only be purchased, possessed, and used in jurisdictions where such activities are legal. Customers are solely responsible for compliance with local laws.
📚 Educational/Informational Use: This repository and its contents are provided for educational and informational purposes only. Nothing herein should be construed as legal advice.
For legal guidance, please consult the California Department of Cannabis Control (DCC) or a qualified attorney specializing in cannabis law.




