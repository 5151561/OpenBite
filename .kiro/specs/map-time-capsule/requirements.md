# Requirements: Map and time capsule

## Introduction

Every place the user ate at becomes a pin. Opening a pin shows a "time capsule": the meals had there, newest first, with the milestone they were for. Places can also be shared as `.openbite` files and imported by friends.

## Requirement 1: Pins

1. The map SHALL show one pin per place that has coordinates, using MapLibre with the OpenFreeMap "liberty" style (no API key).
2. WHEN shop data first arrives THEN the camera SHALL move to fit the pins, even if a default world view was saved earlier.
3. The user SHALL be able to filter pins by tag (for example "good for dates", "late night") and by text.
4. IF the selected place is hidden by the active filter THEN the card SHALL switch to a place that is still visible.

## Requirement 2: Time capsule card

1. WHEN the user taps a pin THEN the system SHALL show a card with the place, its visit count and its latest meals with milestone tags.
2. WHEN the user taps the card THEN the system SHALL open the place detail page.

## Requirement 3: Place location

1. The user SHALL be able to set or move a place's location on a map picker.
2. WHEN the picker opens THEN it SHALL centre on the place, or on the user's location, not on the whole world.
3. The user SHALL be able to add and remove place tags.

## Requirement 4: Share and import

1. The user SHALL be able to share a meal as a card image.
2. The user SHALL be able to share a place as a `.openbite` file (zip with `manifest.json` and menu photos).
3. WHEN a `.openbite` file is imported THEN the system SHALL reject it if it has two manifests, directory entries, path traversal, or photos larger in total than the size limit.
4. Importing the same file twice by tapping quickly SHALL import it once.
5. Shared files SHALL NOT contain API keys, receipts or personal notes.
