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
| Free user | Install without an account, download or import a GGUF model, chat offline, use basic skills, grant scoped file access, approve or deny sensitive actions. |
| Plus user | Everything a Free user can do, plus advanced skills, automation, persistent memory, advanced RAG, multiple models and routing, voice, vision. Pays once. |
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
- Free tier and a one-time Bruce Plus purchase via Google Play Billing.

### Tier rules

1. Free is a complete product: no message limits, time limits, trial periods or
   deliberate slowdowns.
2. Never behind Plus: local inference, bring-your-own-model, GGUF, Hugging Face
   (anonymous and signed in), offline use, conversations, basic memory, basic
   skills, single-file operations.
3. Plus adds capabilities. It never unlocks something Free was blocked from
   doing at a basic level.
4. Free covers single user-requested actions; Plus covers scale, automation and
   orchestration. One file is Free, many files are Plus. One model is Free,
   profiles and routing are Plus. Basic memory and RAG are Free, persistent
   memory and advanced RAG are Plus. Text is Free, voice and vision are Plus.
   Foreground use is Free, background workflows are Plus.
5. Hardware defaults are identical in both tiers. Automatic backend selection
   must perform the same in Free. Plus adds manual tuning, not speed.
6. Plus is one purchase, kept permanently. Plus features added later are
   included in that purchase. No subscriptions, credits or packs.
7. No ads in either tier.
8. The upgrade prompt appears only when the user attempts a Plus operation.
   "Not now" dismisses it. No banners and no unprompted upgrade prompts.
9. No account is needed beyond Google Play for the purchase.
10. Security and privacy features are identical in both tiers: policy engine,
    confirmations, scoped grants, network modes, export and delete.
11. Plus keeps working offline once the purchase has been verified, using the
    locally cached entitlement.
12. A Free feature, once released, never moves to Plus.
13. Only the user can start a purchase. The model can only return a
    Plus-required error; it cannot start a purchase flow.

### Out of scope

- Cloud inference of any kind.
- Dependence on Google Gemini Nano, AICore or Google GenAI libraries.
- Dependence on a specific device manufacturer or NPU.
- Advertising.
- Subscriptions, message credits, token packs or usage fees.
- Mandatory user accounts.
- Distribution outside Google Play.
- Bypassing Android security, permission or background-execution rules.
- Model formats other than GGUF (until a later plan revision adds them).

## Features

Status values: `planned`, `in progress`, `done`, `dropped`. Tier is Free, Plus,
or Both.

### Inference

- **llama.cpp integration** — Native llama.cpp built with the NDK, behind an
  `InferenceEngine` interface (load, unload, generate, stop, capabilities, model
  info). No other module depends on llama.cpp directly. Both. `planned`
- **Streaming generation** — Tokens stream to the caller as generated; generation
  can be stopped. Both. `planned`
- **Backend detection and selection** — Detect CPU features (NEON, FP16, DOTPROD),
  Vulkan and OpenCL; select a backend; always fall back to CPU. NPU is used only
  if a supported backend exposes it. Both. `planned`
- **Memory estimation** — Estimate a model's RAM requirement and compare it with
  usable device RAM before loading. Both. `planned`
- **Advanced hardware tuning** — User control over backend, threads, context and
  similar settings; optional backend benchmarking. Plus. `planned`

### Models

- **Model manager** — Import, install, verify, inspect (architecture, parameters,
  quantisation, context size, file size, compatibility, memory), select, load,
  unload, delete. Model files live outside the database. Both. `planned`
- **Hugging Face discovery** — Search public repositories, inspect metadata, list
  GGUF files, distinguish a repository from a runnable model. Anonymous by
  default. Both. `planned`
- **Hugging Face sign-in** — OAuth ("Sign in with Hugging Face") as the default,
  pasted access token as a fallback. Token stored in Keystore-backed storage.
  Both. `planned`
- **Resumable downloads** — Download, resume, verify file integrity. Both. `planned`
- **Model recommendation** — Recommend a model from RAM, storage, backend, size,
  quantisation; the user can always override. Both. `planned`
- **Multiple model profiles and routing** — Named profiles (Main, Fast, Coding,
  Vision) and automatic model selection per task. Basic in Free, advanced and
  routing in Plus. `planned`

### Assistant

- **BruceRuntime** — Coordinates requests, context, model calls, tool calls,
  memory, permission requests and results. Both. `planned`
- **Agent loop** — Multi-step tool use, bounded by maximum tool calls, maximum
  execution time and resource limits; supports cancellation and structured
  errors. Both. `planned`
- **Conversations** — Local history; export and delete. Both. `planned`
- **Memory** — Conversation memory; long-term user-approved facts; local
  knowledge. Basic/limited in Free, persistent in Plus. `planned`
- **Local RAG** — Text extraction, chunking, local embeddings, local index,
  retrieval. Basic in Free, advanced in Plus. `planned`
- **Untrusted content handling** — Content from files, web pages and tool results
  is passed to the model as data and never grants authority. Both. `planned`

### Policy

- **Capability model** — Capability classes and capabilities as listed in the
  product spec (INFORMATION, FILES, PERSONAL_DATA, SENSORS, COMMUNICATION,
  SYSTEM, NETWORK). Both. `planned`
- **Policy engine** — Policies AUTO, USER_ENABLE, CONFIRM, ALWAYS_CONFIRM, DENY.
  Enforcement order: validate tool, validate arguments, capabilities, Android
  permissions, user grants, resource scope, confirmation, execute, sanitise
  result. Both. `planned`
- **Scoped grants** — Grants bound to a resource scope (for example one SAF
  directory) with duration: once, this chat, until revoked, permanent. Both.
  `planned`
- **Exact-operation confirmation** — Confirmation binds tool ID, arguments,
  target resources, timestamp and policy version; execution is refused if the
  operation differs. Both. `planned`
- **Structured denials** — Tool failures return a code (`UNKNOWN_TOOL`,
  `CAPABILITY_DISABLED`, `RESOURCE_OUTSIDE_SCOPE`, and the others in the spec),
  message, `user_can_change`, `retryable`. Both. `planned`
- **Network modes** — Offline only, Hugging Face only, approved domains, general
  internet. Both. `planned`

### Skills

- **Automatic skills** — Date/time, calculator, battery, device information,
  storage status, network status. Free. `planned`
- **File read** — Read selected files and directories granted via the Storage
  Access Framework. Free. `planned`
- **File create/write** — Create and write single files in granted scopes;
  confirmation on each operation. Free. `planned`
- **File delete** — Delete single files in granted scopes; always-confirm. Free.
  `planned`
- **Advanced skills** — Move/rename files, calendar create/modify/delete,
  sharing, opening apps and URLs, clipboard write, SMS, calls, notifications.
  Plus. `planned`
- **Privacy-sensitive skills** — Location, contacts, camera, microphone; disabled
  by default; camera and microphone need an explicit user action each time.
  Tier is an open question. `planned`

### Interface and platform

- **Main screens** — Chat, Conversations, Models, Model browser, Downloads,
  Skills, Permissions, Memory, Settings. Both. `planned`
- **Confirmation UI** — Shows the exact operation and its targets before
  approval. Both. `planned`
- **Voice** — Speech-to-text, text-to-speech, voice conversations; local where
  practical. Plus. `planned`
- **Vision** — Local multimodal models for images, documents, screenshots. Plus.
  `planned`
- **Advanced automation and background workflows** — Multi-step and multi-file tasks,
  scheduled tasks, reminders, within Android background limits. Plus. `planned`
- **Bruce Plus purchase** — One-time non-consumable Google Play product;
  upgrade prompt shown only when the user attempts a Plus operation. Price shown
  is the localised price from Google Play Billing, never a fixed value.
  `planned`
- **Data ownership controls** — Export and delete conversations, delete memories
  and models, remove permissions, clear local data, disable networking. Both.
  `planned`
- **Theme** — Material 3 theme with a Bruce brand palette (tan and white).
  User setting: System (default), Light, Dark. On Android 12 and later the user
  may turn on wallpaper-based dynamic colour; it is off by default. Both.
  `planned`
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
| Model format | GGUF | llama.cpp native format. |
| Build | Gradle with Kotlin DSL | Android standard. |
| Billing | Google Play Billing | Play-only distribution. |
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
| Plus entitlement | Google Play Billing, cached locally |

No data leaves the device unless the user enables a feature that needs it.

The Room database is not encrypted by the app. It relies on Android app-private
storage and the device's own storage encryption. Owner decision, 2026-09-27.

The database and other app data are excluded from Android cloud backup and
device-to-device transfer.

## Integrations

| System | Used for | Required |
|---|---|---|
| Hugging Face Hub API | Model search, metadata, GGUF download, optional sign-in | No. Bruce works offline once a model is installed. |
| Google Play Billing | Bruce Plus one-time purchase | Only to buy or restore Plus. |
| Android platform APIs | Battery, device, storage, network, SAF, calendar, and other skills | Yes, per skill and per user grant. |

## Constraints

- Inference must work with no network connection.
- No network request is made unless the current network mode allows it.
- The model must not be able to change permissions, grants or policy.
- An approved confirmation must match the executed operation exactly.
- No account is required to install or use the Free tier.
- No ads, no subscriptions. Plus is a one-time purchase.
- The Plus price is set in Google Play only. The app must display the localised
  price returned by Google Play Billing and must not contain a price in code,
  string resources, assets or other shipped copy.
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
- Source is closed; repository is private.

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
| What "Limited" persistent memory and "Basic" RAG mean for Free, as measurable limits. | User |
| Tier for privacy-sensitive skills (location, contacts, camera, microphone). | User |
| Difference between the grant durations "until revoked" and "permanent". | User |
| Tool-calling format for small models (for example grammar-constrained JSON output). To be settled by Phase 1 experiments. | User, from evidence |
| Local embedding model for RAG. | User, from evidence |
| Web search provider for `WEB_SEARCH`. | User |
| Conversation export format. | User |
| Dependency injection approach (Hilt or manual). | Developer, with user approval |
| Whether any crash reporting or telemetry exists. The privacy model implies none. | User |
| Which models Bruce recommends by default. | User, from Phase 0 benchmarks |
| Whether Play Billing and the Plus purchase are part of the MVP release. | User |
| Logo and visual identity: tan working Cocker Spaniel with white blaze. Who produces the artwork. | User |

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
4. **Phase 3 — Bruce Plus.** Play Billing, advanced automation, local RAG,
   multiple models and routing, voice, vision, advanced Android integrations,
   advanced hardware tuning.
5. **Phase 4 — Advanced Bruce.** Background workflows, more Android APIs,
   accessibility integrations, third-party and smart-home integrations, more
   inference backends.

The MVP feature list (spec §54) falls in Phases 1 and 2. MVP success criterion
13 (upgrade to Plus) needs Play Billing, which is in Phase 3. Whether Play
Billing moves into the MVP is an open question.

## Revision history

| Date | Change | Summary |
|---|---|---|
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
