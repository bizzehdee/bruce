# Bruce — project plan

Bruce is a local-first personal AI assistant for Android. It runs open-weight
models on the device and acts on the user's behalf through tools, within
permissions the user grants.

Motto: *Your AI. Your model. Your phone. Your permissions. No ads. No
subscription. Just Bruce.*

## Purpose

Phone users who want an AI assistant today must send their data to a cloud
service, depend on a vendor AI stack (Gemini Nano, AICore), or accept ads and
subscriptions. Bruce runs a model the user chooses entirely on their Android
phone, works offline, and can carry out multi-step tasks using phone
capabilities. The model is never the security boundary: a policy engine decides
what Bruce may do, and the user decides the policy.

Bruce is a personal assistant, not a chatbot. Chat is the main interface to the
assistant, not the product itself.

## Users

| User | Needs to |
|---|---|
| User | Install without an account, download or import a GGUF model, chat offline, use skills, grant scoped file access, approve or deny sensitive actions. Every feature is available to every user at no cost. |
| Hugging Face account holder | Optionally sign in to reach private and authorised gated repositories. |

## Scope

### In scope

- Android app, ARM64 devices, Android 10 (API 29) and later.
- On-device inference with llama.cpp, GGUF model format.
- CPU inference always; Vulkan and OpenCL where the device supports them.
- Model discovery and download from Hugging Face, anonymous or signed in.
- Local model import.
- Agent runtime with tool calling, bounded agent loop, cancellation.
- Capability and policy engine with scoped grants and exact-operation confirmation.
- Local-only storage of conversations, memories, permissions and tool history.
- Every feature free of charge, for every user.
- Distribution through Google Play, F-Droid and signed APKs on GitHub Releases.
- Open source under GPL-3.0-or-later.

### Pricing rules

1. Bruce is free. There is no paid tier, purchase, subscription, credit, pack,
   trial period, message limit or deliberate slowdown.
2. No ads.
3. No account is required for any feature.
4. Security and privacy features apply equally to every user.

### Out of scope

- Cloud inference of any kind.
- Dependence on Google Gemini Nano, AICore or Google GenAI libraries.
- Dependence on a specific device manufacturer or NPU.
- Advertising.
- Any paid tier, in-app purchase or payment flow.
- Subscriptions, message credits, token packs or usage fees.
- Mandatory user accounts.
- Bypassing Android security, permission or background-execution rules.
- Model formats other than GGUF (until a later plan revision adds them).

## Features

Status values: `planned`, `in progress`, `done`, `dropped`.

### Inference

- **llama.cpp integration** — Native llama.cpp built with the NDK, behind an
  `InferenceEngine` interface (load, unload, generate, stop, capabilities, model
  info). No other module depends on llama.cpp directly. `done`
- **Streaming generation** — Tokens stream to the caller as generated; generation
  can be stopped. `done`
- **Backend detection and selection** — Detect CPU features (NEON, FP16, DOTPROD),
  Vulkan and OpenCL; select a backend; always fall back to CPU. Auto uses the CPU;
  a GPU backend is used only when the user picks it, marked experimental, because
  Vulkan gave wrong output on the Pixel 11's PowerVR GPU. NPU is used only if a
  supported backend exposes it. `done`
- **Memory estimation** — Estimate a model's RAM requirement and compare it with
  usable device RAM before loading. `done`
- **Advanced hardware tuning** — User control over backend, threads, context and
  similar settings; optional backend benchmarking. `planned`

### Models

- **Model manager** — Import, install, verify, inspect (architecture, parameters,
  quantisation, context size, file size, compatibility, memory), select, load,
  unload, delete. Model files live outside the database. `planned`
- **Hugging Face discovery** — Search public GGUF repositories through the
  anonymous Hub API. Search results already carry architecture, parameter count,
  context length, licence and gated status, so incompatible or gated models can be
  flagged before any download. For a chosen file, Bruce reads only its GGUF header
  with a partial (range) download to get the layer shape for the memory estimate.
  Only the search text and download requests leave the phone; device details never
  do. `done`
- **Hugging Face sign-in** — OAuth ("Sign in with Hugging Face") as the default,
  pasted access token as a fallback. Token stored in Keystore-backed storage.
  `done` for OAuth; the pasted-token fallback is not built.
- **Resumable downloads** — Download into app storage, resume with range requests
  after interruption, check free space first, and verify the SHA-256 the Hub
  publishes for the file. `done`
- **Model recommendation** — Rank search results for this phone, on the phone:
  whether the model fits usable RAM (memory estimate), whether llama.cpp supports
  its architecture, and an expected speed band from measured benchmarks. Shows a
  fit label on each result; the user can always override. `done`
- **Multiple model profiles and routing** — Named profiles (Main, Fast, Coding,
  Vision) and automatic model selection per task. Basic in Free, advanced and
  routing in `planned`

### Assistant

- **BruceRuntime** — Coordinates requests, context, model calls, tool calls,
  memory, permission requests and results. Skills are loaded as needed rather
  than all described to the model up front, because every tool description costs
  prompt time on the phone (method chosen by TASK-033). `planned`
- **Agent loop** — Multi-step tool use, bounded by maximum tool calls, maximum
  execution time and resource limits; supports cancellation and structured
  errors. `planned`
- **Conversations** — Saved conversations ("sessions"): the drawer lists them,
  newest first; the user starts a new chat, resumes, renames, archives or deletes
  one, and can archive or delete several at once. Archived chats are kept but
  leave the main list. Stored locally; export follows. `done` (export not yet)
- **Context management** — The chat shows how much of the model's context is in
  use and how much is free, with a note that a full context does not stop the
  chat: new messages push the oldest out. When the context overflows, the oldest
  messages are dropped (the system prompt and skills are kept). Optional
  auto-summarise, off by default: when on, older messages are summarised once use
  reaches a user-chosen threshold (85, 90, 95 or 100%). `planned`
- **Memory** — Facts Bruce remembers across chats. Setting: Off (default), On
  per model (each model has its own memory) or On globally (one memory shared by
  all models). Bruce saves facts automatically; the user reviews and deletes them
  in a Memory screen. Local knowledge follows with Local RAG. `planned`
- **Local RAG** — Text extraction, chunking, local embeddings, local index,
  retrieval. Basic in Free, advanced in `planned`
- **Untrusted content handling** — Content from files, web pages and tool results
  is passed to the model as data and never grants authority. `planned`

### Policy

- **Capability model** — Capability classes and capabilities as listed in the
  product spec (INFORMATION, FILES, PERSONAL_DATA, SENSORS, COMMUNICATION,
  SYSTEM, NETWORK). Skills declare the capabilities they need; the model's
  arguments are validated against each skill's schema, refusals are structured,
  and results go back to the model marked as untrusted data. `done` (enforced by
  the policy engine once it exists)
- **Skill states** — Each skill has one user-set state: **Declined** (never
  used), **Ask** (the user confirms every use) or **Accepted** (always allowed,
  never asked again). The user changes states in the Skills screen. Fresh-install
  defaults follow the spec's policies: automatic skills start Accepted, file read
  starts Declined, file create, write and delete start Ask. High-risk skills
  (the spec's always-confirm operations: delete files now; SMS, calls, payments
  and side-effecting web requests later) are flagged as high risk; setting one to
  Accepted shows a warning the user must accept first. The owner chose to allow
  Accepted for high-risk skills with this warning (2026-09-28). `planned`
- **Policy engine** — Applies the skill states. Enforcement order: validate tool, validate arguments, capabilities, Android
  permissions, user grants, resource scope, confirmation, execute, sanitise
  result. `planned`
- **Scoped grants** — File skills act only inside files and folders the user
  granted through the Storage Access Framework, whatever the skill's state.
  Grants last until the user revokes them; the spec's grant durations (once, this
  chat, until revoked, permanent) are replaced by the skill states. `planned`
- **Exact-operation confirmation** — For skills in the Ask state, confirmation binds tool ID, arguments,
  target resources, timestamp and policy version; execution is refused if the
  operation differs. `planned`
- **Structured denials** — Tool failures return a code (`UNKNOWN_TOOL`,
  `CAPABILITY_DISABLED`, `RESOURCE_OUTSIDE_SCOPE`, and the others in the spec),
  message, `user_can_change`, `retryable`. `planned`
- **Network modes** — Offline only, Hugging Face only, approved domains, general
  internet. A fresh install starts in offline only. `done`

### Skills

- **Automatic skills** — Date/time, calculator, battery, device information,
  storage status, network status. `planned`
- **File read** — Read selected files and directories granted via the Storage
  Access Framework. `planned`
- **File create/write** — Create and write single files in granted scopes;
  confirmation on each operation. `planned`
- **File delete** — Delete single files in granted scopes; always-confirm.
  `planned`
- **Folder instructions** — When Bruce works in a granted folder, it looks for
  `AGENTS.md` at the folder root and the `.agents/` directory (for example
  `.agents/skills/*/SKILL.md`). The first time, and whenever they change, Bruce
  shows them and asks whether to follow them for that folder. Followed
  instructions guide how Bruce works there, with `.agents/` files loaded as
  needed; they never grant permissions or change skill states. `planned`
- **Advanced skills** — Move/rename files, calendar create/modify/delete,
  sharing, opening apps and URLs, clipboard write, SMS, calls, notifications.
  `planned`
- **Privacy-sensitive skills** — Location, contacts, camera, microphone; disabled
  by default; camera and microphone need an explicit user action each time.
  `planned`

### Interface and platform

- **Chat interface** — The main screen is a chat conversation in the style of
  Claude, ChatGPT and Gemini: a scrolling message list, a message composer at the
  bottom, and replies that stream in as they are generated. Bruce's actions,
  permission requests and confirmations appear inline in the conversation.
  This familiar layout is the starting point because it is what users expect;
  how Bruce's interface stands apart is decided later. `in progress`
- **Navigation** — A side drawer, as in the ChatGPT, Claude and Gemini apps, holds
  the conversation list and entries for Models and Settings. The chat screen's top
  bar shows the active model; tapping it opens a quick model switcher. The
  permissions management screen is not in the drawer; it is reached from
  Settings. `done`
- **Model management screen** — Installed models with size, quantisation and fit
  label; choose the active model; model details (metadata, memory estimate,
  backend); delete. Import from a file with the system picker. Browse, search and
  download from Hugging Face with fit-ranked results and download progress.
  Per-model settings (context length, backend, threads, temperature) override the
  inference defaults. The last chosen model loads at launch; if none was chosen
  and only one model is installed, that model loads. `done`
- **Model browser recommendations** — The Hugging Face tab opens on a list of
  models suited to the phone, so users need not know a model's name; name search
  stays available for advanced users. Filters: parameter count (buckets), download
  size (buckets), runs on this phone (fit), and task type. Task types are limited
  to those Bruce can run (text generation now; vision and embeddings once
  supported). Sizes in the list are estimated from parameter count and
  quantisation (exact once a repository is opened). `done`
- **Settings screen** — Appearance (theme and dynamic colour, moved from the
  prototype screen); inference defaults (backend, threads, context length);
  network (network mode and Hugging Face sign-in); data and privacy (export and
  delete conversations, delete memories, clear all data, open-source licences);
  a Skills section opening the Skills screen; a link to the permissions management screen; and Diagnostics, the Phase 0 test
  bench (hardware report, backends, benchmarks), available in every build. `in progress`
- **Setup wizard** — Shown on first launch. Walks the user through the choices that
  shape Bruce before first use: network mode, notification permission, and getting
  a first model (import or download). Skills keep their defaults until changed in
  the Skills screen. Every choice can be changed later. `done`
- **Skills screen** — Reached from Settings. Lists every skill with what it does,
  a high-risk flag where it applies, and its state (Declined, Ask, Accepted),
  which the user changes there. `planned`
- **Permissions screen** — Reached from Settings. Lists the files and folders
  Bruce has been granted and the Android permissions it holds, each revocable.
  `planned`
- **Main screens** — Chat, Conversations, Models, Model browser, Downloads,
  Skills, Permissions (reached from Settings), Memory, Settings. `planned`
- **Confirmation UI** — Shows the exact operation and its targets before
  approval. `planned`
- **Response notifications** — When a reply finishes while Bruce is not on
  screen, a notification says so; tapping it opens the chat. Generation continues
  while the app is in the background. Needs the notification permission,
  requested when first needed. `planned`
- **Voice** — Speech-to-text, text-to-speech, voice conversations; local where
  practical. `planned`
- **Vision** — Local multimodal models for images, documents, screenshots.
  `planned`
- **Advanced automation and background workflows** — Multi-step and multi-file tasks,
  scheduled tasks, reminders, within Android background limits. `planned`
- **Bruce Plus purchase** — One-time Google Play purchase unlocking a paid
  tier. Dropped: every feature is free. `dropped`
- **Data ownership controls** — Export and delete conversations, delete memories
  and models, remove permissions, clear local data, disable networking.
  `planned`
- **Theme** — Material 3 theme with a Bruce brand palette (tan and white).
  User setting: System (default), Light, Dark. On Android 12 and later the user
  may turn on wallpaper-based dynamic colour; it is off by default.
  `done`
- **Default assistant** — Bruce can be chosen as Android's digital assistant app
  and opens from the system assistant gesture or button. No always-on wake word:
  low-power hotword detection is limited to privileged system apps.
  Milestone is an open question. `planned`
- **App logo** — Flat, 2D, stylised reddish-tan working Cocker Spaniel with a white
  stripe down the middle of the head, three-quarter pose looking from left to right,
  with neck and collar. Master SVG in `docs/branding/`. Launcher icon has a
  transparent background and a themed monochrome layer; the Play icon sits on warm
  cream. `done`
- **Store listing** — Play Store feature graphic, listing text (name, short
  description, full description) and prepared answers for the Play Console setup
  questions, kept as Markdown in the repository. Phone screenshots follow once
  the product UI exists (Phase 1). `planned`
- **Privacy policy** — Public privacy policy describing Bruce's local-only data
  handling, required by Google Play. Published on the owner's website; the
  contact email is the one given in `docs/store/`. `planned`
- **Future surfaces** — Widget, Quick Settings tile, share-sheet actions,
  shortcuts. `planned`

## Stack

| Component | Choice | Why |
|---|---|---|
| App language | Kotlin | Native Android; required for Compose and the Android SDK. .NET is a poor fit for an NDK-heavy Android app. |
| UI | Jetpack Compose with Material 3 | Current Android UI toolkit; the UI must conform to Material Design 3. |
| Concurrency | Kotlin Coroutines and Flow | Streaming tokens and cancellation map to Flow. |
| Persistence | Room (SQLite) | Structured local data; model files stay on disk. |
| File access | Storage Access Framework | Scoped, user-granted access instead of broad storage permission. |
| Native | C++, Android NDK, CMake, JNI | llama.cpp is C/C++. |
| Inference | llama.cpp, git submodule at a pinned commit | Explicit version pin; upgrades visible in history. |
| Native backends | ggml backends as runtime-loaded libraries: 7 Android CPU variants, Vulkan, OpenCL. Native libraries are extracted on install. | One APK runs the best code path each phone supports. |
| Build tools | Khronos Vulkan-Headers, SPIRV-Headers, OpenCL-Headers, OpenCL-ICD-Loader (link only) as pinned submodules; host `glslc` | The NDK lacks these headers, and its `glslc` is too old for llama.cpp's shaders. |
| HTTP and JSON | Android's `HttpURLConnection` and `org.json` | Built in; no new dependencies for a small API surface. |
| Model format | GGUF | llama.cpp native format. |
| Build | Gradle with Kotlin DSL | Android standard. |
| Unit tests | JUnit Jupiter 6 (successor to JUnit 5), MockK, Robolectric | Kotlin-idiomatic mocking. Robolectric runs Compose UI tests on the JVM so Kover counts UI branches. |
| UI / end-to-end tests | Compose UI tests (instrumented, JUnit 4 runner) | Playwright does not apply to native Android. The Compose test rule requires JUnit 4. |
| Native tests | GoogleTest | JNI glue and native helpers. |
| Coverage | Kover | Branch coverage gate. |
| CI | GitHub Actions | Build (including NDK), unit tests, coverage gate. No emulator tests in CI. |
| Minimum OS | Android 10 (API 29), ABI arm64-v8a | Scoped storage baseline; Vulkan 1.1 widely available. |
| Application ID | `com.bizzeh.bruce` | Chosen by the owner. |

## Data

| Entity | Stored in |
|---|---|
| Conversations, messages | Room database, app-private storage |
| Memories | Room database |
| Tool calls, tool results | Room database |
| Model metadata | Room database |
| Model files (GGUF) | App-private file storage, outside the database |
| Permissions, grants, scopes | Room database; SAF URI permissions persisted by Android |
| Settings | Jetpack DataStore (Preferences) |
| Hugging Face token | Android Keystore-backed encrypted storage |

No data leaves the device unless the user enables a feature that needs it.

The Room database is not encrypted by the app. It relies on Android app-private
storage and the device's own storage encryption. Owner decision, 2026-09-27.

The database and other app data are excluded from Android cloud backup and
device-to-device transfer.

## Integrations

| System | Used for | Required |
|---|---|---|
| Hugging Face Hub API | Model search, metadata, GGUF download, optional sign-in | No. Bruce works offline once a model is installed. |
| Android platform APIs | Battery, device, storage, network, SAF, calendar, and other skills | Yes, per skill and per user grant. |

## Constraints

- Inference must work with no network connection.
- No network request is made unless the current network mode allows it.
- The model must not be able to change permissions, grants or policy.
- An approved confirmation must match the executed operation exactly.
- No account is required to install or use any feature.
- No ads and no payments of any kind.
- Android 10 (API 29) minimum; arm64-v8a.
- Branch coverage of at least 90%, excluding generated code, migrations and
  composition-root wiring. `@Composable` functions are excluded because the
  Compose compiler generates branches in them; branching logic must live in
  plain functions that the gate counts.
- Secrets (including the Hugging Face token) are never stored in source, logs or
  plain preferences.
- Android background-execution and permission rules are respected; never
  bypassed.
- App data is excluded from Android cloud backup and device-to-device transfer.
- Source is open under GPL-3.0-or-later. Every shipped dependency must be
  GPL-3.0-compatible; GPL-2.0-only code must not be added.
- F-Droid requires the app to build from source with no proprietary dependencies.

## Test devices

| Device | Availability | Notes |
|---|---|---|
| Sony Xperia XZ Premium | Always | 4 GB RAM. Deliberate stand-in for a modern budget phone. LineageOS, Android 13. |
| Sony Xperia 1 II | Always | Android 12. |
| Google Pixel 11 | Intermittent | Benchmarks on it are recorded when it is available; tasks do not wait for it. |

## Open questions

| Question | Owner |
|---|---|
| App signing arrangements (Play App Signing, upload key custody). | User |
| Tool-calling format for small models (for example grammar-constrained JSON output). To be settled by Phase 1 experiments. | User, from evidence |
| Local embedding model for RAG. | User, from evidence |
| Web search provider for `WEB_SEARCH`. | User |
| Conversation export format. | User |
| Dependency injection approach (Hilt or manual). | Developer, with user approval |
| Whether any crash reporting or telemetry exists. The privacy model implies none. | User |
| Which models Bruce recommends by default. | User, from Phase 0 benchmarks |
| Default assistant: milestone. | User |
| How Bruce's interface should differ from mainstream chatbots, once the chat interface exists. | User |
| Default assistant: confirm the Android requirements (voice-interaction service, assistant role) against current Android documentation. | Developer, before planning tasks |
| Privacy policy URL on the owner's website. | User |

## Milestones

1. **Phase 0 — Technical prototype.** llama.cpp on Android, GGUF load and
   inspection, streaming generation, CPU feature detection, Vulkan and OpenCL,
   backend selection with CPU fallback, memory estimation, benchmarks on real
   devices. Minimal debug screen only. CI in place.
2. **Phase 1 — Basic Bruce.** Chat UI, model manager, local conversations,
   Hugging Face discovery, sign-in and resumable downloads, automatic skills,
   basic policy system, tool calling.
3. **Phase 2 — Real assistant.** File skills, agent loop, scoped permissions,
   confirmation UI, better memory, calendar, sharing, notifications.
4. **Phase 3 — Full assistant.** Advanced automation, local RAG,
   multiple models and routing, voice, vision, advanced Android integrations,
   advanced hardware tuning.
5. **Phase 4 — Advanced Bruce.** Background workflows, more Android APIs,
   accessibility integrations, third-party and smart-home integrations, more
   inference backends.

The MVP feature list (spec §54) falls in Phases 1 and 2. MVP success criterion
13 (upgrade to Plus) no longer applies.

## Revision history

| Date | Change | Summary |
|---|---|---|
| 2026-09-28 | Changed | Skill framework and capability model done: skill definitions, argument validation, structured denials, untrusted-result envelope. |
| 2026-09-28 | Changed | Backend choices list only backends this phone can use (Auto, CPU, and usable GPUs). |
| 2026-09-28 | Changed | Saved conversations done (drawer list, resume, rename, archive, delete, bulk actions, Archived view, delete all chats); export still to come. |
| 2026-09-28 | Changed | Model browser recommendations and filters done; list sizes are estimates, exact when a repository is opened. |
| 2026-09-28 | Changed | Auto backend always uses the CPU; Vulkan and OpenCL only when chosen, marked experimental (Vulkan gave wrong output on the Pixel 11). |
| 2026-09-28 | Changed | Setup wizard settled: welcome, network mode, notification permission and a first model; no skill toggles or appearance. |
| 2026-09-28 | Added | Folder instructions: Bruce reads `AGENTS.md` and `.agents/` in a granted folder and asks before following them. |
| 2026-09-28 | Changed | Hugging Face sign-in by OAuth marked done after a real sign-in on a phone; pasted-token fallback not built. |
| 2026-09-28 | Added | Context indicator with drop-oldest overflow and optional auto-summarise; memory setting (off, per model, global) with automatic saving and review; response notifications; archive and bulk archive/delete of chats; skills loaded as needed; the only installed model loads at launch. |
| 2026-09-28 | Changed | Skill permissions are per-skill states (Declined, Ask, Accepted) set in a Skills screen under Settings; high-risk skills warn before Accepted; grant durations dropped; Permissions screen holds folder grants and Android permissions. |
| 2026-09-28 | Added | Model browser recommendations with filters (parameter count, download size, fit, task type); saved conversations defined. |
| 2026-09-28 | Changed | Statuses from TASK-024 to TASK-028: chat interface and Settings screen in progress, navigation done, Hugging Face sign-in in progress. |
| 2026-09-28 | Changed | Hugging Face discovery, resumable downloads, model recommendation, model management and network modes marked done; sign-in awaits a real sign-in. |
| 2026-09-27 | Added | First-launch setup wizard covering network mode, permission toggles and a first model. |
| 2026-09-27 | Changed | Hub API uses built-in HTTP and JSON; test bench kept for everyone as Settings > Diagnostics; fresh installs start offline only; Hugging Face sign-in by OAuth now. |
| 2026-09-27 | Changed | Permissions management screen is reached from Settings only, not from the drawer. |
| 2026-09-27 | Added | Side-drawer navigation, a Model management screen and a Settings screen; open-source licences are shown under Settings, closing that open question. |
| 2026-09-27 | Changed | Hugging Face discovery, resumable downloads and model recommendation detailed from verified Hub API behaviour. |
| 2026-09-27 | Changed | Theme setting marked done (System/Light/Dark, optional dynamic colour, stored with DataStore). |
| 2026-09-27 | Added | Chat interface as the main screen, in the style of Claude, ChatGPT and Gemini; replaces the spec's §8 guidance that Bruce should not look like a conventional chatbot. |
| 2026-09-27 | Changed | Memory estimation marked done: weights plus KV cache against usable RAM. |
| 2026-09-27 | Changed | llama.cpp integration, streaming generation, and backend detection and selection marked done; GPU order provisional until benchmarked. |
| 2026-09-27 | Changed | Logo done: vectorised from the owner's reference image, keeping neck and collar; transparent launcher icon, cream Play icon. |
| 2026-09-27 | Changed | Logo artwork comes from the owner and is vectorised, replacing the hand-built SVG approach. |
| 2026-09-27 | Added | App logo (hand-built SVG), Play Store feature graphic, listing text and setup answers, and a privacy policy published on the owner's website; screenshots deferred to Phase 1. |
| 2026-09-27 | Changed | Native backends are runtime-loaded libraries with CPU variants, Vulkan and OpenCL; build needs Khronos headers and a host glslc. |
| 2026-09-27 | Changed | Licence GPL-3.0-or-later; distribution through Google Play, F-Droid and GitHub Releases APKs. |
| 2026-09-27 | Removed | Bruce Plus and all payment: every feature is free, with no paid tier or purchase; source becomes open, licence to be chosen. |
| 2026-09-27 | Changed | Default assistant is Free tier. |
| 2026-09-27 | Added | Bruce selectable as Android's default digital assistant; no always-on wake word. |
| 2026-09-27 | Changed | Test device OS versions recorded; JUnit Jupiter 6; `@Composable` functions excluded from the branch gate. |
| 2026-09-27 | Added | Material 3 UI; theme setting System/Light/Dark with optional dynamic colour; DataStore for settings; Robolectric for JVM UI tests. |
| 2026-09-27 | Changed | Plus price removed from the plan; price is set in Google Play and must never be hardcoded in the app. |
| 2026-09-27 | Added | Tier rules for Free and Plus. |
| 2026-09-27 | Changed | Single-file create, write and delete are Free; multi-file operations and move/rename stay Plus. Overrides spec §29. |
| 2026-09-27 | Added | App data excluded from Android cloud backup and device-to-device transfer. |
| 2026-09-27 | Changed | Xperia XZ Premium named as the budget-phone (4 GB RAM) test device. |
| 2026-09-27 | Changed | Room database will not be encrypted by the app; encryption-at-rest question closed. |
| 2026-09-27 | Added | Test devices: Xperia XZ Premium (custom ROM), Xperia 1 II, Pixel 11 (intermittent). |
| 2026-09-27 | Changed | Application ID set to `com.bizzeh.bruce`; signing arrangements remain open. |
| 2026-09-27 | Created | Initial plan from the Bruce product spec and requirements conversation. |
