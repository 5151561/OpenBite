---
inclusion: fileMatch
fileMatchPattern: ["shared/core/database/**", "shared/core/data/**", "shared/core/model/**"]
---

# Data conventions (Room KMP, `openbite.db`)

- **Primary keys:** UUID strings from the injected `IdGenerator`. No auto-increment.
- **Money:** integer minor units (`priceMinor`; ¥1280 -> 1280, $12.50 -> 1250) plus an ISO 4217 currency. Never `Double` for money. Decimal places come from ISO 4217, not from the locale.
- **Time:** UTC millis + IANA zone (`Asia/Tokyo`) + local date (`2025-11-27`). Milestones match on local date, so changing time zone never moves an anniversary.
- **Lists:** queried lists (people, tags) use join tables; display-only lists (allergens) are JSON strings.
- **Photos:** files in app-private storage; the database stores **relative paths** only. Resolve them through `PhotoFiles`, which checks canonical paths segment by segment.
- **Snapshots:** dining log items store name and price snapshots. Re-scanning a menu never rewrites history.
- **Foreign keys:** deleting a place cascades to tags, scans, dishes and logs. Deleting a dish sets `dishId` to null and keeps the snapshot. Milestones and people survive place deletion.
- **Status:** `draft` / `done` strings exist only in the mapper; everywhere else use the `DiningStatus` enum. `createdBy` is `user` or `assistant`.

## Changing the schema

1. Bump `version` in `OpenBiteDatabase.kt`.
2. Write a `Migration` in `Migrations.kt`. Never use destructive migration.
3. Build once so Room exports `schemas/.../<version>.json`, and commit it.
4. Extend the migration test: it compares every table's columns, indexes (unique, column order) and foreign keys (target, cascade).

## Privacy

- Data extraction rules exclude the database, photos and DataStore from cloud backup and device transfer. Keep it that way.
- API keys live only in `SecretStore` (Keystore AES/GCM). Never in the database, logs, exports, `.openbite` files or assistant results.
