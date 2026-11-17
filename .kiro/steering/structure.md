---
inclusion: always
---

# Project structure

```text
Hackathon-kiroween/
├── build-logic/convention/   # convention plugins; also blocks feature -> data/network edges
├── gradle/libs.versions.toml # version catalog
├── shared/                   # KMP core (Android target now, iOS later)
│   ├── core/model/           # plain @Serializable data classes
│   ├── core/database/        # Room entities, DAOs, migrations; schemas/ is committed
│   ├── core/network/         # VisionModel: Anthropic + OpenAI-compatible SSE, sample model
│   ├── core/data/            # repositories, seed import, platform interfaces
│   ├── core/domain/          # use cases, AssistantApi, milestone maths
│   ├── core/testing/         # shared fakes for tests
│   └── feature/*/            # shared ViewModels + UI state + Koin module per screen group
├── androidApp/
│   ├── app/                  # Application, Koin wiring, Navigation 3 shell, platform impls, seed assets
│   ├── core/designsystem/    # Material 3 theme and shared composables
│   ├── feature/*/            # Compose screens, one module per shared feature
│   └── assistant/            # AppFunctions service entry point; calls AssistantApi only
├── tools/seed/draw_menus.py  # generates the sample menu photos (no third-party images)
├── docs/                     # plan and HTML design mocks (19 screens)
└── .kiro/                    # steering, specs, hooks
```

## Where things go

| You are adding | Put it in |
| --- | --- |
| A data class shared by layers | `:shared:core:model` |
| A table or column | `:shared:core:database` + a migration (see `data-conventions.md`) |
| Business logic | a use case in `:shared:core:domain` |
| Screen state and events | a ViewModel in `:shared:feature:<name>` |
| A Compose screen | `:androidApp:feature:<name>` |
| A phone capability (camera, location, files) | interface in `PlatformServices.kt`, impl in `:androidApp:app` |
| An assistant function | `AssistantApi` first, then a thin wrapper in `:androidApp:assistant` |
| A user-visible string | `values/strings.xml` (zh-CN) **and** `values-en/strings.xml` |

## Naming

- Packages: `com.openbite.core.<layer>`, `com.openbite.feature.<name>`, `com.openbite.android.*`.
- Use cases end in `UseCase` and expose `operator fun invoke`.
- Navigation keys end in `Key`; screens pop themselves with `pop(key)`.
- Commits start with the step id: `U1`-`U6` UI steps, `F*` features, `R*` review fixes, `H*` hardening, `V*` device fixes.
