---
inclusion: always
---

# Product: OpenBite

OpenBite turns one restaurant order into a memory your phone's AI assistant can recall.

## One-line pitch

Snap a menu you can't read, order from it, and the app quietly writes a dining memory that an on-device AI assistant can bring back next year.

## Hackathon framing

- Event: Kiroween 2026. Category: **Frankenstein**.
- The "monster" is four parts that were never meant to fit, each feeding the next:
  vision LLM (order) -> local Room database (memory) -> native map (place) -> Android AppFunctions (recall).
- Deadline: 2026-12-05 06:00 GMT+8. Feature freeze: 11/30. After the freeze only bug fixes.

## Who it is for

A traveller or couple eating at small places abroad, who cannot read handwritten menus and wants to remember where and what they ate for anniversaries.

## Core loop (the only path the demo must show)

The user actively does two things: take a photo and pick dishes. Everything else is generated.

1. Photo -> GPS candidate read from EXIF, then EXIF stripped.
2. Vision model streams a structured menu (original name, translation, price, possible allergens).
3. User picks dishes and allergies -> full-screen "show the staff" ticket.
4. Tapping "Ordered" creates a draft dining log with everything pre-filled.
5. 90 minutes later a local notification asks for a rating, short note and photos.
6. The place appears as a pin on the map with its "time capsule" of past meals.
7. 7 days before a milestone the app reminds the user, and the assistant reads past meals via AppFunctions.

## Product rules

- **Installs and works alone.** No server, no account, no cloud database. Judges can use it without an API key (seed data + demo mode).
- **Honest privacy.** All data stays on the phone. The only data that leaves is an EXIF-stripped menu or receipt photo sent to the model the user chose (BYOK). Say so in the UI.
- **Allergens are advisory.** Every allergen shown carries a "for reference only, confirm with staff" note, and the ticket always shows the original text next to the translation.
- **Mobile only.** Android now; iOS (SwiftUI + App Intents) reuses the KMP core after the hackathon.
- Terminology: say "phone AI assistant" (中文：手机 AI 助手), never "Claude Desktop" or "local server".
- Out of scope until after the hackathon: cloud sync, accounts, desktop server, MCP server, mini-program screenshot import, dish TTS, year-in-review, on-device vision model.
