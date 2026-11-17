# Tasks: Menu scan and order

Each task ended with `assembleDebug` and tests passing and one commit. Commit ids are listed for traceability.

- [x] 1. Platform services for photos
  - `ImageProcessor` (GPS read, EXIF strip, 1568 px), `LocationProvider`, `PhotoStore`, `SecretStore` (Keystore AES/GCM)
  - _Requirements: 1.1, 1.2, 1.4, 3.6_ · `b975b93`
- [x] 2. Claude SSE vision model, sample model and `ScanMenuUseCase`
  - Stream dishes, merge into place menu, save in one transaction
  - _Requirements: 2.1, 2.2, 2.4, 4.1_ · `b975b93`
- [x] 3. Order screen while scanning, ticket, "Ordered" creates a draft, taste profile
  - _Requirements: 5.1, 5.3, 5.5_ · `22c32f5`
- [x] 4. Keyboard and small screens
  - 4.1 Inputs stay above the keyboard · `d2a2b59`
  - 4.2 Two-column scan layout on short screens · `12ea92e`
  - 4.3 Status bar icons readable in both themes · `2d08db0`
- [x] 5. Treat truncated streams as failures
  - _Requirements: 2.3_ · `0f3ab4d`
- [x] 6. Delete raw camera files from `cache/scan`
  - _Requirements: 1.6_ · `0ade95d`
- [x] 7. Order session in the data layer; retry as a command; go back when the session is gone
  - _Requirements: 5.6_ · `e634efa`
- [x] 8. Multi-provider AI with no hardcoded models
  - 8.1 Anthropic / OpenAI / Gemini / DeepSeek / custom; `/models` list; red-PNG vision probe
  - 8.2 Demo mode switch (off by default); guidance when no model, bad key or retired model
  - _Requirements: 3.1-3.5, 4.1_ · `fd8eda3`
- [x] 9. Currency inference and locale formatting
  - _Requirements: 2.5_ · `48c932d`
- [x] 10. Ticket in the menu's language; 13 built-in phrase tables; validated AI tables for others
  - _Requirements: 5.2, 5.4_ · `268f3a0`
- [x] 11. Tests
  - Scan ViewModel `dde42ea`, order and ticket ViewModels `7dc8108`, menu merge `1a221c4`
- [ ] 12. Build a 10-menu test set of real handwritten menus; at least 8 must be usable
  - Own photos or clearly CC-licensed images only
- [ ] 13. Pick the most stable menu for the demo recording and keep a backup screen recording
