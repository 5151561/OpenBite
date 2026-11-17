---
inclusion: fileMatch
fileMatchPattern: ["androidApp/**/*.kt", "androidApp/**/res/**"]
---

# Android UI rules

- **Design first.** New screens follow `docs/OpenBite Android 界面设计 · Material 3.html`. A screen that is not in the design gets a mock approved before code.
- **Edge-to-edge.** Every screen handles system bars. Inputs and bottom buttons must stay above the keyboard (`adjustResize` + `navigationBars ∪ ime` insets). Full-screen dialogs are edge-to-edge too.
- **Small and landscape screens.** If height is short, switch to two columns rather than pushing primary buttons off screen.
- **Navigation 3.** A screen closes itself with `pop(key)`; the same key is never pushed twice onto one stack. Bottom sheets that own a ViewModel are back-stack entries, so the ViewModel is cleared when they leave.
- **One-shot events** (snackbars, errors) are consumed once and do not reappear after rotation.
- **Images.** Decode local thumbnails with `inSampleSize` for the display size.
- **Strings.** `values/strings.xml` is Simplified Chinese, `values-en/strings.xml` is English. Add both. Counts use `plurals`. Brand and provider names are `translatable="false"`.
- **Money and dates** are formatted by the phone's locale (`€12.50` in zh-CN, `12,50 €` in de-DE; `Nov 27, 2025` in English).
- **Wording.** Say "phone AI assistant" / "手机 AI 助手".
