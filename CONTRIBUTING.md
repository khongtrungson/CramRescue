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
haven't started (e.g. no `microcard/` until that milestone begins).

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
- `campaignId` always comes from explicit navigation state (a nav argument), passed down from the
  Campaign the user chose. There is no "latest" or "current" Campaign anywhere — no global
  current-campaign holder, and repositories never infer one. (`getLatestCampaign()` was removed
  in M14.)
- Query shapes are `getXForCampaign(campaignId)`, never `getLatestX()`.

## Campaign Management

- Home lists every Campaign (`CampaignListPolicy`): exam still ahead first, nearest exam first;
  then exam reached, most recent first; ties by id. Status (upcoming / "Khẩn cấp" / "Đã đến giờ
  thi") is derived from `CampaignCountdown` — never persisted. One shared 30 s tick re-derives the
  rows; no per-row timers and no database work per tick. Past Campaigns are kept until the user
  deletes them.
- Editable: name and exam time only (`UpdateCampaignUseCase`, rules in the shared
  `CampaignValidator`, also used by Create). The exam-time rule (future, ≤ 7 days) applies only
  when the exam time changes, so a past Campaign can be renamed. The subject archetype is
  **not** editable: the analysis prompt depends on it (FR-7) and a stored analysis doesn't record
  which archetype produced it. An edit changes only the Campaign row — never materials, analysis,
  cards, attempts or missions — and triggers no OCR/AI.
- Deletion (`DeleteCampaignUseCase`) needs the explicit confirmation dialog. `deleteCampaignGraph`
  reads the documents' file paths and deletes the Campaign row in one transaction; every owned
  table cascades from it (verified by `CampaignDeletionTest`). Only after commit are the files
  removed via `MaterialFileStore.deleteOwnedFile` (app materials dir only — a user's original is
  never touched; a missing file counts as deleted). Room and the filesystem are not atomic: a DB
  failure deletes nothing and can be retried; a file that can't be removed is left unreferenced
  in app-private storage and reported as a count. No background cleanup.
- After deleting, navigate to Home with `popUpTo(Home) { inclusive = true }` so Back can never
  reopen a screen of the deleted Campaign. Screens re-read their Campaign on (re)entry (Home list,
  Cockpit), so edits show up without restarting.

## Document / Page Ownership

- A document belongs to exactly one Campaign; a page belongs to exactly one SourceDocument.
- OCR data must remain traceable to its source document *and* page.
- Feature code uses explicit ids — never "latest document/campaign" assumptions.
- Page indices use one internal convention: **0-based in domain and storage**, displayed as
  page N+1 in the UI. Don't mix the two.
- PDF rendering must preserve a coordinate relationship suitable for future Source Grounding:
  bounding boxes are relative to the rendered page bitmap, whose dimensions are stored per page.

## OCR Source Coordinates

- OCR spatial data must remain traceable to its SourceDocument/page.
- Domain/persistence models must not depend on ML Kit-specific geometry types (e.g. no
  `android.graphics.Rect` outside the ML Kit engine implementation).
- Bounding boxes must use a documented coordinate system relative to the source image/page (see
  `OcrResult` in `domain/model/`).
- Persist enough source-dimension/page information to interpret those coordinates later.
- Do not discard bounding boxes because Source Grounding depends on them.

## Word (.docx) Documents

- Only `.docx` (Office Open XML) is supported. Legacy `.doc` and password-protected files (both
  OLE containers) are detected by magic bytes and rejected with a "save as .docx" message — never
  claim `.doc` support.
- Extraction is local and offline: `DocxTextExtractor` (`java.util.zip` + a streaming
  `XmlPullParser`, no third-party library, no OCR, no network). Treat the file as hostile: size
  caps on decompressed parts, entry-count cap, DOCTYPE never processed, and a truncated main part
  is MALFORMED (partial text is never imported). Body paragraphs and table rows only; text boxes,
  drawings, headers/footers, footnotes, comments and tracked deletions are excluded.
- Word text is stored in `document_text_blocks` (identity `(documentId, orderIndex)`), not in the
  OCR tables: Word has no pages, pixel dimensions or bounding boxes, and none may be invented.
  A Word document is one logical section, `pageIndex = WORD_LOGICAL_SECTION_INDEX` (0), which is
  never displayed as a page — the UI says "đoạn N" / "văn bản Word".
- Import is all-or-nothing: extract → reject empty / > 10,000 words (FR-3, never truncated) → save
  the `source_documents` row and its paragraphs in one transaction. On any failure the app-owned
  copy is deleted.
- Page ceilings count Word as `ceil(words / 500)` page-equivalents (FR-3 assumption).
- Word contributes `"id:w@extractedAt"` to the analysis fingerprint; the image/PDF format is
  unchanged so existing analyses stay current.

## AI Analysis (Gemini) Boundary

- Gemini networking lives only in `data/remote/` (`GeminiStudyAnalysisClient`, `GeminiJson`). The
  domain talks to it through `StudyAnalysisClient`; no HTTP, JSON or Gemini types leave `data/`.
- The model id, base URL and timeouts live only in `GeminiConfig`. Don't name a model anywhere else.
- Only the official endpoint (`generativelanguage.googleapis.com`) over HTTPS with default
  certificate validation. No other network endpoint, no analytics, no telemetry, no own backend.
- Gemini receives **text only** — OCR text and locally extracted Word paragraphs — never images,
  PDFs, Word files, rendered pages or bounding boxes.
- AI input is built by `AnalysisSource.assemble(campaignId, …)` in document → page → block order.
  Each block gets a request-local ref (`B1…Bn`) mapping to `(documentId, pageIndex, orderIndex)` —
  the stable block identity (OCR row ids change on reprocessing).
- OCR text is untrusted data: it only appears between the material delimiters, and the system
  instruction says it must not be followed as instructions.
- Model output is untrusted too: structured JSON → typed `AnalysisDraft` →
  `StudyAnalysisValidator` → domain → Room. Any invalid field or unknown source ref rejects the whole
  response. Never parse free text or Markdown from the model.
- Product limits (3-Bullet Budget, Code Green discarding) are enforced on the client after
  validation, not left to the prompt.
- An analysis belongs to exactly one Campaign (`campaign_analyses.campaignId` is the primary key).
  Regeneration replaces it in one transaction, and only after validation succeeds, so a failed
  regeneration never touches the existing analysis. Staleness is detected by comparing
  `sourceFingerprint` with the campaign's current material.
- Tests use a fake `StudyAnalysisClient`; no test may need a real key.

## MicroCards

- MicroCards are **built locally** from the campaign's current analysis (`MicroCardBuilder`); M8
  makes no AI call. Each Code Red / Code Yellow priority becomes at most one card; Code Green never
  becomes a card (FR-5 keeps only its count).
- Cards copy the priority's content and source refs into `micro_cards` / `micro_card_source_refs`
  so they survive analysis regeneration; `analysisGeneratedAtEpochMillis` records which analysis
  generation they came from.
- A card set is current only if the analysis still exists, is not stale, and has the same
  `generatedAt`. Otherwise it is shown as stale until the user explicitly rebuilds it — never
  rebuilt or re-analysed automatically.
- Rebuilding requires a current analysis, validates the whole set (every source ref must resolve
  to a current OCR block of the same campaign), then replaces the old set in one transaction.
- Card source refs use the same `(documentId, pageIndex, blockOrderIndex)` identity as analysis
  refs; `OcrResult.blockAt(ref)` recovers the block and its bounding box for Source Grounding.
- Deleting a material removes card refs to it (like analysis refs); cards stay, shown as stale.
- Don't add study state (mastery, scheduling, cloze keywords) to MicroCard before the Active
  Recall milestone needs it.

## Active Recall / Study State

- Active Recall is fully local: no Gemini request, no key read, no network. M9 classes must not
  depend on `StudyAnalysisClient` or `GeminiCredentialRepository`.
- Cloze (FR-11) is chosen by `RecallPromptBuilder`: deterministic rules (formula / `O(...)`,
  technical identifier, number, short definition tail), never randomness, never AI. When no
  safe target exists it falls back to `RecallPrompt.General`. Prompts are recomputed from the
  immutable card content and never stored; card text is never modified.
- Before reveal, show only the prompt — never notes, sources or other bullets.
- Study state is append-only `study_attempts` (FR-12 levels `NOT_REMEMBERED` / `PARTIAL` /
  `MASTERED`, labelled "Chưa nhớ" / "Tạm ổn" / "Đã thuộc"). A card's mastery is its latest
  attempt; don't add mutable progress fields to MicroCard.
- `study_attempts.microCardId` is `SET NULL` on card deletion: rebuilding cards keeps history
  (with a triage/title snapshot) and new cards never inherit old results. Campaign deletion
  cascades.
- The study flow is the `RecallSession` state machine (QUESTION → REVEALED → SAVING → next /
  COMPLETED); transitions only from explicit events, so recomposition or a double tap can't
  save twice or skip a card. Scheduling / spaced repetition is out of scope until specified.

## Pass Probability / Micro-missions

- Fully local and derived: no Gemini request, no key read, no network, no stored score.
- "Xác suất an toàn" follows requirements.md FR-9 exactly: `0.70 × Red completion rate + 0.30 ×
  Yellow completion rate`, integer 0..100. It is the PRD's deterministic readiness heuristic, not
  a calibrated probability. Policy lives only in `PassProbabilityPolicy`:
  completion = latest attempt "Đã thuộc"; an empty tier's weight is dropped; floor rounding; no
  time factor. No cards → "unavailable", never 0%.
- Only current cards count: latest attempt per current card id; attempts of replaced cards are
  history only.
- Micro-missions (FR-10): `MissionSelectionPolicy` picks ≤ 7 non-mastered Red, then Yellow cards
  (within a tier: Chưa nhớ → never studied → Tạm ổn → card order). No padding, no Green, no
  randomness. The chosen ids/order are frozen in `micro_missions` so a mission can be paused and
  resumed; progress is derived from attempts newer than `baselineAttemptId`.
- Missions run through the M9 Active Recall engine (`RecallSession.startInOrder`) — never a
  second study implementation. No new or resumed mission once the exam time has passed.
- The Cockpit reloads study state on screen entry, never per countdown tick.

## Source Grounding

- Read-only and local: no OCR, no AI, no network, no file writes. Resolution is by persisted
  identity only — card (campaign-scoped) → ref `(documentId, pageIndex, blockOrderIndex)` →
  `ocr_results` page (canonical `sourceWidthPx/sourceHeightPx`) → `ocr_text_blocks` box. Never
  resolve or "repair" a source by matching OCR text; a broken reference stays unavailable.
- The card must actually cite the requested page, and the document must belong to the campaign;
  navigation ids are not trusted. If the page was re-OCR'd after the card was built, block
  identity no longer holds → unavailable ("SOURCE_CHANGED").
- Canonical space = the exact pixels ML Kit processed: the upright photo (EXIF applied —
  `InputImage.fromFilePath` does this; verified by `SourceOrientationAuditTest`) or the rendered
  PDF page. Viewer images must be decoded with `decodeUprightSampledBitmap`, never raw
  `BitmapFactory`, and a bitmap whose aspect differs from the source dims is refused.
- All mapping lives in the pure `SourceCoordinateMapper` (Fit + centered offsets, in pixels —
  never treat OCR pixels as dp). Display rectangles are never persisted; they are recomputed
  on every container size change. Boxes are drawn individually; the union is only for focus;
  visual padding is never used to correct geometry.
- PDFs reuse the M6 `PdfPageRenderer` and render only the requested page.
- Word sources (FR-14) are grounded as a **text snippet**: the document's paragraphs with the
  cited ones highlighted (`TextGroundedSourceContent`). Same identity rules — exact
  `orderIndex`, SOURCE_CHANGED if re-extracted after the card was built, no text matching — and
  no page image or bounding box is ever simulated.
- In Active Recall, sources become reachable only after Reveal.

## Emergency Speed-run

- FR-13: every current Code Red card whose latest attempt isn't "Đã thuộc" — Yellow, mastered
  and Green skipped, no count cap ("toàn bộ"); 10 minutes is a displayed target, not enforced.
  Order (assumption): Chưa nhớ → never studied → Tạm ổn → card order, via the shared
  `MissionSelectionPolicy.priority`. Policy lives only in `SpeedRunPolicy`.
- Emergency comes only from `CampaignCountdown` (FR-8); `SpeedRunPolicy.window` separates the
  open window from "exam reached". Speed-run is user-started, never automatic, and the Cockpit
  decides visibility from the ticking countdown without per-tick database work.
- It is a `StudyMode` of the one Active Recall engine (selection frozen at session start, same
  prompts/reveal/assessment/attempts/sources) — never a second study screen. Not persisted
  (FR-13 has no resume); results are ordinary `StudyAttempt`s feeding FR-9 unchanged.
- Known MVP semantics: a Speed-run result on a card of an unfinished Micro-mission advances that
  mission (attempts carry no provenance).

## Secrets (BYOK Gemini Key)

- The key is stored only through `GeminiCredentialRepository`: AES-GCM with a non-exportable
  Android Keystore key, ciphertext in the `gemini_credential` prefs file, which is excluded from
  backup/device transfer. `EncryptedSharedPreferences` is deprecated — don't reintroduce it.
- Never put the key in Room, logs, exception messages, UI state, saved state, navigation
  arguments, URLs (it goes in the `x-goog-api-key` header only), or committed test fixtures.
- Read the key immediately before a call and don't hold it; ViewModels never hold it.
- When testing on a device, type a real key on the device keyboard. `adb shell input text`
  is recorded in the system `adbd` log.

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
