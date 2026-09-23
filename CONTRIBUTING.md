# Contributing to CramRescue 48h

This document defines the development conventions for this repository. It applies equally to
human contributors and AI coding agents. Read it before implementing a feature so new code lands
in the right place and follows the same shape as what's already here.

`requirements.md` is the product source of truth. This file is the *engineering* rulebook: how
code is organized and how layers are allowed to depend on each other.

## Architecture

```
Presentation → Domain ← Data
```

- **Presentation** (`presentation/`): Jetpack Compose screens, ViewModels, `UiState` data classes,
  and navigation. Owns UI state and user interaction. Talks to the app only through use cases
  (and, where a use case would be overkill, directly through a repository interface).
- **Domain** (`domain/`): domain models, business rules, use cases, and repository *interfaces*.
  Pure Kotlin. This is the core of the app and must stay independent of how data is stored or how
  the UI is built.
- **Data** (`data/`): Room database, DAOs, entities, repository *implementations*, entity↔domain
  mappers, and (in later milestones) other data sources such as OCR output or the Gemini client.

Both Presentation and Data depend on Domain. Domain depends on nothing else in this project.

## Package Conventions

```
presentation/<feature>/<subfeature>/   e.g. presentation/campaign/create/
presentation/navigation/                NavGraph.kt, Routes.kt

domain/model/
domain/repository/
domain/usecase/<feature>/               e.g. domain/usecase/campaign/

data/local/database/                    AppDatabase.kt
data/local/dao/
data/local/entity/
data/mapper/                            <Feature>Mapper.kt (entity <-> domain conversions)
data/repository/                        <Feature>RepositoryImpl.kt
```

Group by feature, not by layer-of-one. A feature with multiple screens (e.g. campaign creation and
confirmation) gets its own subpackage per screen under `presentation/<feature>/`.

Only create a package when it holds a real file. Don't pre-create empty packages for features that
haven't started (e.g. no `ocr/`, `gemini/`, `microcard/` until those milestones begin).

## Naming Conventions

| Role | Pattern |
|---|---|
| Compose screen | `XxxScreen` |
| ViewModel | `XxxViewModel` (+ `XxxViewModelFactory` where manual DI needs one) |
| UI state | `XxxUiState` |
| Use case | `XxxUseCase` |
| Repository interface | `XxxRepository` |
| Repository implementation | `XxxRepositoryImpl` |
| Room entity | `XxxEntity` |
| Room DAO | `XxxDao` |
| Mapper file | `XxxMapper.kt`, with `Xxx.toEntity()` / `XxxEntity.toDomain()` extension functions |

Use descriptive, domain-oriented names (`CreateCampaignUseCase`, not `CampaignManager` or
`CampaignHelper`).

## Dependency Rules

- Composables must not access a DAO or the Room database directly.
- Composables must not reference Room entities — only domain models.
- ViewModels talk to the app through use cases (or a repository interface when a use case adds no
  value); they never import Room types.
- Domain must not depend on Room, Compose, or any other Presentation/Data implementation detail.
- Repository *interfaces* live in `domain/repository/`; repository *implementations* live in
  `data/repository/`.
- Room entities stay in `data/local/entity/` and never leak into Presentation or Domain.
- Business validation belongs in Domain (typically inside the relevant use case), not in the
  Composable or ViewModel.

## Feature Development Rules

- Implement one milestone at a time, in the order the project has agreed on.
- Do not add dependencies for future milestones.
- Do not create placeholder architecture (empty packages, unused classes) for features that
  haven't started.
- Keep every completed milestone buildable — don't leave the project in a broken state between
  features.
- Do not silently change product requirements; `requirements.md` is the source of truth for
  behavior.
- Mark implementation assumptions explicitly (in the PR/commit description or a code comment when
  the reasoning isn't obvious).
- Prefer the simplest implementation that still respects the architecture boundaries above — no
  speculative abstractions (no `BaseViewModel`, `BaseRepository`, `BaseUseCase`, generic `Result`
  wrappers, manager/helper/coordinator/provider packages, etc.) unless a second concrete use case
  already exists that needs it.

## Testing Rules

- Business rules (use cases, validation) should have local JVM unit tests.
- Persistence behavior (DAO queries, Room migrations) should be tested where relevant, typically as
  instrumented tests.
- A successful compile is not sufficient verification — run the tests.
- Important user flows should be manually exercised on an emulator/device before calling a feature
  done, not just assumed to work because the code compiles.
- Existing tests must continue to pass after a refactor. If a refactor moves a class, move its test
  alongside it and update the test's package/imports too.

## UI / Design System

- **Light, neutral UI is the primary visual direction.** The default theme (`CramRescueTheme`,
  `darkTheme = false`) is a calm, light, high-readability surface — not the older permanently-dark
  "Emergency Cockpit" look. A dark scheme is preserved in `ui/theme/Color.kt` /`Theme.kt` for future
  dark-mode support (`requirements.md` §5.4 calls for it eventually), but it is not the default.
- **Visual inspiration, not visual copying.** "Anki-like" means light backgrounds, clean surfaces,
  simple controls, and minimal visual noise — it does not mean reusing Anki's branding, icons, or
  exact layout. CramRescue keeps its own identity.
- **Red / Yellow / Green are semantic, not decorative.** `CodeRed`, `CodeYellow`, and `CodeGreen`
  (in `ui/theme/Color.kt`) exist to carry ER Triage meaning: critical/urgent, important/medium
  priority, and safe/optional/positive, respectively. They may also be used for emergency/urgency
  emphasis once that feature exists (e.g. a countdown going critical).
- **Never make a semantic color the app's dominant chrome.** No permanently red/yellow/green
  screen backgrounds, and no "the button is red because the app has an emergency theme." A
  semantic color should show up as a small, intentional signal — a badge, an indicator, a
  countdown accent — against an otherwise calm neutral UI. `AccentPrimaryLight` (a neutral dark
  tone, not a triage color) is what buttons and primary interactive elements use instead.
- **No hard-coded colors in feature screens.** A screen composable should reference
  `MaterialTheme.colorScheme.*` roles (or a named constant from `ui/theme/Color.kt` for a specific
  semantic case), never a raw `Color(0x...)` literal. If a screen needs a color the theme doesn't
  expose yet, add a named, documented constant to `ui/theme/Color.kt` — don't inline it.
- **Normal UI stays visually calm.** Everyday screens (Home, forms, confirmations) should read as
  restrained and readable, not urgent. Save color and emphasis for the moments that are actually
  urgent.
- **Accessibility and readability come before decoration.** Prefer clear contrast and simple
  layouts over gradients, heavy shadows, or effects that don't serve legibility. This matters more
  as later milestones (Cockpit, MicroCards, Pass Probability, Speed Run) build on this same design
  language — they should read as the same calm product with meaningful color, not a new visual
  system each time.

## Campaign Data Ownership

- Campaign is the top-level ownership boundary for campaign-specific data (materials, and
  whatever future per-campaign data is added).
- Campaign-owned data must be explicitly associated with the relevant Campaign, directly (a
  `campaignId` column/field) or through a clearly defined parent relationship.
- Downstream features must not call `getLatestCampaign()` to infer ownership. `campaignId` should
  come from explicit application/navigation state (e.g. a nav argument), passed down from wherever
  the campaign was actually loaded.
- `getLatestCampaign()` is currently only an MVP startup/resume policy (which campaign Home
  reopens into) — it must never become a global assumption that the app only has one Campaign.
- New schemas/APIs should stay multi-campaign-ready even while multi-campaign *management* UI
  doesn't exist yet: query shapes like `getXForCampaign(campaignId)`, not `getLatestX()`.

## OCR Source Coordinates

- OCR spatial data must remain traceable to its SourceDocument/page.
- Domain/persistence models must not depend on ML Kit-specific geometry types (e.g. no
  `android.graphics.Rect` outside the ML Kit engine implementation).
- Bounding boxes must use a documented coordinate system relative to the source image/page (see
  `OcrResult` in `domain/model/`).
- Persist enough source-dimension/page information to interpret those coordinates later.
- Do not discard bounding boxes because Source Grounding depends on them.

## Dependency Management

- Inspect the existing version catalog (`gradle/libs.versions.toml`) and Gradle configuration
  before adding anything.
- Do not guess dependency or plugin versions — verify compatibility with the project's actual
  Kotlin/AGP/Gradle versions first.
- Add a dependency only when the current milestone actually requires it.
- Prefer platform/Android functionality over a third-party library when it's sufficient for the
  requirement.

## Investigation Discipline

- Investigate only enough to make the next implementation decision.
- Do not repeatedly inspect already-resolved dependencies or configuration.
- Prefer compile/test feedback over speculative dependency investigation.
- Successful commands should not be rerun without a specific reason.
- Broad research should only be performed when blocked by a concrete issue.
- Keep verification bounded to the acceptance criteria of the current milestone.
