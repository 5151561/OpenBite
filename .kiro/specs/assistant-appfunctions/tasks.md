# Tasks: Phone AI assistant (AppFunctions)

- [x] 1. `AssistantApi` in shared domain with the five functions and limits
  - _Requirements: 1-5_ · `f07027a`
- [x] 2. `:androidApp:assistant` service entry point, KSP-generated service and XML, app metadata
  - _Requirements: 6.3_ · `f07027a`
- [x] 3. "Assistant log" badge on list, meal and fill-in screens
  - _Requirements: 5.2_ · `f07027a`
- [x] 4. Verify all five functions on an Android 16 phone with `adb shell cmd app_function`
  - `createDiningLog` draft visible in the app after reloading seed data
- [x] 5. Reject out-of-range input instead of replacing it with defaults
  - _Requirements: 1.3, 3.3_ · `1925c5d`
- [x] 6. Pure error mapping (no leaked details), field mapping tests, generated-XML snapshot test
  - _Requirements: 6.2_ · `f6012a4`
- [x] 7. `AssistantApi` edge tests
  - _Requirements: 1.1, 2.2, 5.2_ · `04a1da8`
- [x] 8. `@Throws` on suspend functions so Swift can call `AssistantApi`
  - `2f56afe`
- [ ] 9. Demo script: milestone notification, then the adb call next to the phone screen showing the real JSON
