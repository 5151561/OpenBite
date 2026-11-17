# Requirements: Phone AI assistant (AppFunctions)

## Introduction

OpenBite has no server. Instead it exposes its local data to the phone's AI assistant through Android AppFunctions (Android 16+). The assistant can recall past meals for an upcoming milestone and log a meal by voice, but cannot change or delete anything. The logic is shared Kotlin (`AssistantApi`) so iOS App Intents can reuse it.

## Requirement 1: Upcoming milestones

**User story:** As a user, I want to ask my assistant "what's coming up and where did we go last time?".

1. WHEN `upcomingMilestones(daysAhead)` is called THEN the system SHALL return milestones within `daysAhead` days, with the people they are about and up to 5 earlier meals tagged with each, all before the next occurrence.
2. `daysAhead` SHALL accept 1-366; 0 SHALL mean 30.
3. IF `daysAhead` is negative or above 366 THEN the system SHALL throw `INVALID_ARGUMENT`.

## Requirement 2: Query dining logs

1. WHEN `queryDiningLogs` is called with any of `milestoneId`, `personName`, `dateFrom`, `dateTo` THEN the system SHALL return matching logs, newest first, at most 50, each with dishes, ratings, notes and the menu-price total.
2. A blank `milestoneId` SHALL mean no milestone filter.

## Requirement 3: Search places

1. WHEN `searchPlaces` is called THEN the system SHALL return at most 50 places that have all requested tags.
2. WHEN `lat` and `lon` are given THEN results SHALL be within `radiusKm` (default 2, max 50) and sorted by distance.
3. IF `radiusKm` is negative, NaN or above 50, or a tag is too long, THEN the system SHALL throw `INVALID_ARGUMENT`.

## Requirement 4: Place menu

1. WHEN `getPlaceMenu(placeId)` is called THEN the system SHALL return up to 300 dishes with how many times each was ordered and its average rating.
2. IF the place does not exist THEN the system SHALL throw `ELEMENT_NOT_FOUND`.

## Requirement 5: Create a dining log (append only)

1. WHEN `createDiningLog` is called with a place, 1-30 items (each by `dishId` or `name`, 1-20 portions), an optional milestone and a note of at most 500 characters THEN the system SHALL create a **draft** log timed now and return its id.
2. The log SHALL have `createdBy = "assistant"` and the UI SHALL show an "assistant log" badge.
3. The assistant SHALL NOT be able to create places, or update or delete any existing data.

## Requirement 6: Privacy and errors

1. No result SHALL contain photos, file paths, receipts (photo or paid amount) or API keys.
2. Unknown errors SHALL be logged and returned without internal details.
3. The app metadata SHALL explain to the assistant what OpenBite is and what each function is for.
