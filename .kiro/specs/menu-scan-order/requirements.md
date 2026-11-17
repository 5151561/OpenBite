# Requirements: Menu scan and order

## Introduction

A traveller photographs a menu they cannot read. A vision model the user chose turns it into a structured, translated menu, the user orders from it, and the app shows a ticket the staff can read. This is the first part of the OpenBite core loop and the longest part of the demo.

## Glossary

- **Vision model**: an LLM reached with the user's own key (BYOK) through the Anthropic Messages or OpenAI Chat Completions format.
- **Demo mode**: a setting (off by default) that uses bundled sample menus and never goes online.
- **Order session**: in-memory state of one scan-and-order flow, kept in the data layer.
- **Ticket**: the full-screen "show the staff" page.

## Requirement 1: Capture and strip the photo

**User story:** As a traveller, I want to take or pick a menu photo, so that I don't type anything.

1. WHEN the user takes a photo or picks one from the gallery THEN the system SHALL read the photo's GPS coordinates as the place location candidate.
2. WHEN the photo has no GPS AND a new place is being created THEN the system SHALL ask for location permission and take one current location fix.
3. IF location permission is denied THEN the system SHALL continue without a location.
4. WHEN a photo is prepared for upload THEN the system SHALL re-encode it as JPEG with a long edge of at most 1568 px AND remove all EXIF data.
5. WHILE "keep coordinates" is off THEN the system SHALL NOT store photo GPS or a current location for a new place.
6. WHEN the scan succeeds, a photo is added, or the user leaves the scan screen THEN the system SHALL delete the raw camera file from `cache/scan`.

## Requirement 2: Stream a structured menu

**User story:** As a traveller, I want dishes to appear one by one, so that I can start choosing before the scan ends.

1. WHEN a scan starts THEN the system SHALL stream dishes with category, original name, translated name, price, currency and possible allergens.
2. WHEN a dish arrives THEN the order screen SHALL show it immediately and allow choosing it.
3. IF the stream ends without `message_stop`, or with `stop_reason=max_tokens`, THEN the system SHALL treat the scan as failed AND SHALL NOT save a partial menu.
4. WHEN a scan of an existing place succeeds THEN the system SHALL merge dishes into the place menu, trimming whitespace and keeping existing dish ids.
5. WHEN a menu shows no currency THEN the system SHALL infer one in this order: user choice, receipt, menu, place, place location, fallback currency, phone region.

## Requirement 3: Choose a model without hardcoded names

**User story:** As a user, I want to use whichever AI provider I already pay for.

1. The system SHALL support Anthropic, OpenAI, Google Gemini, DeepSeek and a custom OpenAI-compatible endpoint.
2. WHEN the user connects a provider THEN the system SHALL fetch its full model list from `/models`, newest first, searchable and refreshable.
3. The system SHALL NOT contain hardcoded model names or default model lists.
4. WHEN the user picks a model that was never checked THEN the system SHALL send a 64x64 red PNG and SHALL allow the model only if it answers "red", AND SHALL remember the result.
5. IF the chosen model was retired, the key is wrong, or no model is chosen THEN the scan screen SHALL explain the problem and link to the provider page.
6. API keys SHALL be stored only in Keystore-encrypted storage.

## Requirement 4: Demo mode and judges without a key

1. WHILE demo mode is on THEN scanning menus and reading receipts SHALL use bundled samples AND SHALL NOT go online.
2. WHEN the app starts for the first time THEN it SHALL import the Kyoto seed data, so every screen works without a key.

## Requirement 5: Order and ticket

**User story:** As a traveller, I want to show the staff exactly what I want and what I can't eat.

1. The user SHALL be able to set a quantity of 0-99 for each dish and see the total in the menu currency.
2. The ticket SHALL show each dish in the menu's language with a line in the phone's language when the two differ.
3. The ticket SHALL include the user's taste profile (allergies, dislikes) written in the menu's language.
4. WHEN the menu language has a built-in phrase table (13 languages) THEN the system SHALL use it; OTHERWISE it SHALL ask the chosen model to translate the English table, validate keys, count, placeholders and length, cache the result per language, and label it "AI translated".
5. Allergen information SHALL always carry "for reference only, confirm with staff".
6. IF the process is recreated and the order session is gone THEN the order and ticket screens SHALL go back instead of showing empty state.
