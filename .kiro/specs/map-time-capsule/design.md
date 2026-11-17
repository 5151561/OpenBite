# Design: Map and time capsule

## Components

| Component | Module | Job |
| --- | --- | --- |
| `mapPins`, `MapPin.matches` | domain (`MapCapsule.kt`) | pure functions: pins from places and logs, tag and text filters |
| `MapViewModel`, `PickLocationViewModel` | `:shared:feature:map` | state: pins, filter, selected place, camera intent |
| Map screen | `:androidApp:feature:map` | MapLibre `MapView` in Compose, OpenFreeMap style |
| `OpenBiteFile`, `SimpleZip` | domain | `.openbite` encode / decode; pure-Kotlin zip with an iOS `actual` over system zlib |
| `IncomingFileSource` | platform interface | reads an imported file with a byte limit |

## Camera race (fixed in F4)

The first version saved the initial world viewport before shop data arrived, so the camera never moved to Kyoto. The camera now waits for the first non-empty pin list, then fits it once. The location picker had the same race and uses the same rule.

## `.openbite` safety

Decoding runs on the injected Default dispatcher. Entries are checked before anything is written: one `manifest.json`, no directories, no `..` or absolute paths, and a total byte cap. Photos are stored without compression (`STORED`) because they are already JPEG. The Android `java.util.zip` path and `SimpleZip` are tested against each other.

## Testing

- Pin and filter functions; selected card falls back when filtered out (`63afe48`).
- `.openbite` regression tests: two manifests, oversized photos, directory entries, on both zip implementations.
- Import and share ViewModels: double tap imports once, results consumed once.
