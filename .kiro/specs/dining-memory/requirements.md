# Requirements: Dining memory and milestones

## Introduction

Ordering should leave a memory with no extra typing. The app creates a draft when the user orders, nudges them after the meal to add a rating and photos, and ties meals to milestones (anniversaries, birthdays) so they can be recalled later.

## Glossary

- **Dining log**: one meal at one place. Status is `draft` or `done`; `createdBy` is `user` or `assistant`.
- **Milestone**: a dated event, one-off or yearly, about one or more people.
- **Snapshot**: dish name and price copied into a log item, so later menu edits never change history.

## Requirement 1: Draft on order

**User story:** As a diner, I want my order to become a memory automatically.

1. WHEN the user taps "Ordered" THEN the system SHALL create a draft dining log with place, dishes, quantities, price snapshots, UTC time, IANA time zone and local date.
2. IF the menu belongs to no saved place THEN the system SHALL create the place in the same transaction.
3. WHEN a draft is created THEN the system SHALL schedule a follow-up notification 90 minutes later.
4. WHEN the user first taps "Ordered" THEN the system SHALL ask for notification permission.

## Requirement 2: Fill in the memory

1. The user SHALL be able to rate each dish and the whole meal from 1 to 5, write a short note, add photos, tag a milestone and add companions.
2. WHEN the user leaves the edit screen with unsaved changes THEN the system SHALL ask before discarding them; with no changes it SHALL just go back.
3. IF saving fails THEN the system SHALL keep the edits and show an error.
4. Photos added and then discarded SHALL be deleted from app storage in the application scope, even if the screen is closed.

## Requirement 3: Receipt archive

1. The user SHALL be able to photograph the shop's receipt; the chosen model reads line items and the paid total.
2. The system SHALL show the difference between the paid total and the ordered total.
3. The "Save to this meal" button SHALL stay above the keyboard.
4. Receipts SHALL never be returned to the phone AI assistant.

## Requirement 4: Milestones

**User story:** As a partner, I want to be reminded before our anniversary with the places we went before.

1. The user SHALL be able to create, edit and delete milestones with title, type, date, yearly or one-off, people and days of advance reminder.
2. A yearly milestone on 29 February SHALL fall on 28 February in non-leap years.
3. WHEN a milestone is N days or fewer away, where N is its reminder setting (default 7), THEN the system SHALL send one local notification per milestone per year.
4. WHEN a milestone is edited THEN the system SHALL reset this year's reminder state for it.
5. Milestone matching SHALL use the local date, so travelling across time zones never moves it.

## Requirement 5: Search and privacy

1. The user SHALL be able to search memories and places by dish, place, note or person.
2. The user SHALL be able to turn off keeping coordinates.
3. The database, photos and settings SHALL be excluded from cloud backup and device transfer.
4. The user SHALL be able to clear all memories, which also clears `cache/scan` and `cache/share`.

## Requirement 6: Delete a place

1. WHEN the user deletes a place THEN the system SHALL show what will be deleted (dishes, logs, photos) and that it cannot be undone.
2. WHEN confirmed THEN the system SHALL delete the place, tags, menu photos, dishes and its logs, photos and receipts in one transaction, keep milestones and people, cancel follow-up reminders and delete the photo files.
