---
inclusion: fileMatch
fileMatchPattern: ["androidApp/assistant/**", "**/AssistantApi.kt"]
---

# Phone AI assistant boundary (AppFunctions)

The assistant reaches OpenBite only through five functions. Logic lives in the shared `AssistantApi`; `:androidApp:assistant` only converts types and maps errors, so iOS App Intents can reuse the same API later.

| Function | Kind |
| --- | --- |
| `upcomingMilestones` | read |
| `queryDiningLogs` | read |
| `searchPlaces` | read |
| `getPlaceMenu` | read |
| `createDiningLog` | append-only (creates a draft) |

## Hard rules

- Four functions are read-only and one is append-only. The assistant must never update or delete existing data, and must never create places.
- Logs created by the assistant set `createdBy = "assistant"` and show the "assistant log" badge in the UI.
- Results never contain photos, file paths, receipts (photo or paid amount) or API keys.
- Validate every input against the `ASSISTANT_*` limits in `AssistantApi.kt`. Out-of-range values throw `INVALID_ARGUMENT`; never silently replace them with defaults.
- Map unknown errors to a generic error and log them; never leak exception messages to the caller.
- Function ids are `com.openbite.android.assistant.BaseOpenBiteAppFunctionService#<name>`. Renaming a function breaks assistants: update the generated-XML snapshot test on purpose, never by accident.

## Testing on a device

```bash
adb shell cmd app_function list-app-functions | grep -A3 com.openbite.android
adb shell cmd app_function execute-app-function \
  --package com.openbite.android.debug \
  --function 'com.openbite.android.assistant.BaseOpenBiteAppFunctionService#upcomingMilestones' \
  --parameters '{"daysAhead":30}'
```
