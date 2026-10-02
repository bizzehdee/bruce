# Bruce — project plan

Bruce is a local-first AI sidekick for Android. It runs open-weight models on
the device and acts on the user's behalf through skills, within permissions the
user grants. The user picks the sidekick's personality: Bruce (the default) or
Milo.

Motto: *Your AI. Your model. Your phone. Your permissions. No ads. No
subscription. Just Bruce.*

## Purpose

Phone users who want an AI assistant today must send their data to a cloud
service, depend on a vendor AI stack (Gemini Nano, AICore), or accept ads and
subscriptions. Bruce runs a model the user chooses entirely on their Android
phone, works offline, and can carry out multi-step tasks using phone
capabilities. The model is never the security boundary: a policy engine decides
what Bruce may do, and the user decides the policy.

Bruce is a sidekick, not a chatbot. Chat is the main interface to the
sidekick, not the product itself. User-facing text says "sidekick"; technical
text keeps terms such as agent loop and runtime. The tone leans, lightly, into
dog puns: small UI copy (loading and empty states), the personalities' replies,
and the README and store text. Errors, warnings, permissions and confirmations
stay plain.

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
- **Thinking** — Models reply without thinking first (reasoning before the
  answer), because on a phone it costs tens of seconds per reply. A model whose
  template always opens a thinking block (LFM2.5 2.6B) keeps thinking: without it
  that model calls tools for almost every message (owner, 2026-10-01). A setting
  turns thinking on for models that can switch it, with a per-model choice.
  `done` (TASK-110)
- **Advanced hardware tuning** — User control over backend, threads, context and
  similar settings; optional backend benchmarking. `done` (Settings and each
  model's settings; benchmarks in Diagnostics)

### Models

- **Model manager** — Import, install, verify, inspect (architecture, parameters,
  quantisation, context size, file size, compatibility, memory), select, load,
  unload, delete. Model files live outside the database. Each model shows the
  RAM it is expected to use at its context size, as a share of the phone's RAM.
  Context sizes run from 2K to 64K; by default (Auto) Bruce picks the biggest the
  phone can run that keeps the model's total memory within 6 GB, or for a model
  over 6 GB on its own, the biggest up to 16K the phone can run; always within
  what the model was trained for (owner, 2026-10-02; TASK-111).
  `done`
- **Remote model servers** — Connect to the user's own (or any) llama.cpp server,
  or another OpenAI-compatible one, so the model runs there instead of on the
  phone. HTTPS anywhere, plain HTTP only on the local network with a warning; an
  optional API key stored encrypted. Adding a server is the permission for that
  address alone, and Offline mode blocks it. Skills still run on the phone under
  the same policy. Servers' models are listed with installed ones. `done`
- **Hugging Face discovery** — Search public GGUF repositories through the
  anonymous Hub API. Search results already carry architecture, parameter count,
  context length, licence and gated status, so incompatible or gated models can be
  flagged before any download. For a chosen file, Bruce reads only its GGUF header
  with a partial (range) download to get the layer shape for the memory estimate.
  Only the search text and download requests leave the phone; device details never
  do. `done`
  A model published without GGUF files (safetensors only) cannot run in Bruce, so
  searching for it by name or address says so and lists the GGUF copies made from
  it. A name or address of a repository that has GGUF files shows that repository.
  `done` (TASK-102)
- **Hugging Face sign-in** — OAuth ("Sign in with Hugging Face") as the default,
  pasted access token as a fallback. Token stored in Keystore-backed storage.
  `done` (a pasted token must be a read or fine-grained one)
- **Resumable downloads** — Download into app storage, resume with range requests
  after interruption, check free space first, and verify the SHA-256 the Hub
  publishes for the file. `done`
- **Model recommendation** — Rank search results for this phone, on the phone:
  whether the model fits usable RAM (memory estimate), whether llama.cpp supports
  its architecture, and an expected speed band from measured benchmarks. Shows a
  fit label on each result; the user can always override. `done`
- **Multiple model profiles and routing** — Named profiles (Main, Fast, Coding,
  Vision) and automatic model selection per task. `in progress` (profiles chosen per chat
  done, TASK-098; automatic routing not planned yet, owner)

### Assistant

- **BruceRuntime** — Coordinates requests, context, model calls, tool calls,
  memory, permission requests and results. The model asks for a skill in its own
  chat template's tool format (llama.cpp's chat code), with Bruce's own format and
  a grammar for models without tool support (ADR 0001). Skills are described in
  full while there are few, then as a one-line list with a skill's full
  description loaded when it is used, once a prompt budget is passed. `done`
  (the one-line list was measured and dropped: full definitions stay, TASK-054)
- **Agent loop** — Multi-step tool use, bounded by maximum tool calls, maximum
  execution time and resource limits; supports cancellation and structured
  errors. `done` (5 tool calls and 3 minutes per turn)
- **Conversations** — Saved conversations ("sessions"): the drawer lists them,
  newest first; the user starts a new chat, resumes, renames, archives or deletes
  one, and can archive or delete several at once. Archived chats are kept but
  leave the main list. Stored locally; export follows. `done` (export not yet)
- **Context management** — When "Show context window" is on (Appearance, off by
  default; owner, 2026-10-02), the chat shows how much of the model's context is
  in use, green to amber to red as it fills, and how much is free, with a note that a full context does not stop the
  chat: new messages push the oldest out. When the context overflows, the oldest
  messages are dropped (the system prompt and skills are kept). Optional
  auto-summarise, off by default: when on, older messages are summarised once use
  reaches a user-chosen threshold (85, 90, 95 or 100%). `done`
- **Memory** — Facts Bruce remembers across chats. Setting: Off (default), On
  per model (each model has its own memory) or On globally (one memory shared by
  all models). Bruce saves facts automatically; the user reviews and deletes them
  in a Memory screen. Local knowledge follows with Local RAG. `done`
- **Local RAG** — Text extraction, chunking, local embeddings, local index,
  retrieval. `done` (index of granted files with EmbeddingGemma-300M, TASK-096; search skill, TASK-097)
- **Untrusted content handling** — Content from files, web pages and tool results
  is passed to the model as data and never grants authority. `done`

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
  Accepted for high-risk skills with this warning (2026-09-28). `done` (set in the
  Skills screen once it exists)
- **Policy engine** — Applies the skill states. Enforcement order: validate tool, validate arguments, capabilities, Android
  permissions, user grants, resource scope, confirmation, execute, sanitise
  result. `done` (used by the runtime once tool calling exists)
- **Scoped grants** — File skills act only inside files and folders the user
  granted through the Storage Access Framework, whatever the skill's state.
  Grants last until the user revokes them; the spec's grant durations (once, this
  chat, until revoked, permanent) are replaced by the skill states. The model
  names a target as `<grant name>/<folder>/<file>`, resolved only through the
  granted folder's real children. `done`
- **Exact-operation confirmation** — For skills in the Ask state, confirmation binds tool ID, arguments,
  target resources, timestamp and policy version; execution is refused if the
  operation differs. `done`
- **Structured denials** — Tool failures return a code (`UNKNOWN_TOOL`,
  `CAPABILITY_DISABLED`, `RESOURCE_OUTSIDE_SCOPE`, and the others in the spec),
  message, `user_can_change`, `retryable`. `done`
- **Network modes** — Offline only, Hugging Face only, approved domains, general
  internet. A fresh install starts in offline only. `done`

### Skills

- **Automatic skills** — Date/time, calculator, battery, device information,
  storage status, network status. `done` (in chat once tool calls are shown there)
- **File read** — Read selected files and directories granted via the Storage
  Access Framework. `done`
- **File create/write** — Create and write single files in granted scopes;
  confirmation on each operation. `done`
- **File delete** — Delete single files in granted scopes; always-confirm.
  `done`
- **Folder instructions** — When Bruce works in a granted folder, it looks for
  `AGENTS.md` at the folder root and the `.agents/` directory (for example
  `.agents/skills/*/SKILL.md`). The first time, and whenever they change, Bruce
  shows them and asks whether to follow them for that folder. Followed
  instructions guide how Bruce works there, with `.agents/` files loaded as
  needed; they never grant permissions or change skill states. `done`
- **Android settings** — Search and list the phone's settings and read their
  values. Change the few Android lets an app change (such as brightness, screen
  timeout, auto-rotate and sound levels) once the user grants "Modify system
  settings", asking before each change; for any other setting, open the right
  Android settings page so the user changes it. `done`
- **Approved sites** — In the Approved sites network mode, the first request to a
  new site shows a card in the chat: Allow once, Always allow this site, or Don't
  allow. Always-allowed sites are listed in Settings, where they can be removed.
  `done`
- **Web pages** — Fetch a web page (GET only) as plain text, size-capped and
  marked as untrusted data. No forms, uploads, cookies or sign-ins. Only where the
  network mode allows the site. `done`
- **Web search** — Search the web through the Brave Search API (its LLM Context
  endpoint) with the user's own key, entered in Settings and stored encrypted on
  the phone. Bruce ships no key. Only where the network mode allows it. `done`
  Brave needs a payment card even for its free credit, so Bruce adds free
  providers: Wikipedia search, which needs no key, and LangSearch, a free general
  web search with the user's own free key. Reading search engines' result pages
  is ruled out by their robots.txt and terms (owner, 2026-10-02). `planned` (TASK-113)
- **Advanced skills** — Move/rename files, calendar create/modify/delete,
  sharing, opening apps and URLs, clipboard write, SMS, calls, notifications.
  `in progress` (move and rename, TASK-085; calendar, TASK-086; sharing, opening apps and URLs, TASK-087; clipboard write, TASK-088; messages and calls prepared for the user, TASK-089; notes as notifications, TASK-090)
- **Privacy-sensitive skills** — Location, contacts, camera, microphone; disabled
  by default; camera and microphone need an explicit user action each time.
  `done` (location, TASK-091; contacts, TASK-092; camera, TASK-093; no microphone skill, owner: voice input covers it)

### Interface and platform

- **Chat interface** — The main screen is a chat conversation in the style of
  Claude, ChatGPT and Gemini: a scrolling message list, a message composer at the
  bottom, and replies that stream in as they are generated. Bruce's actions,
  permission requests and confirmations appear inline in the conversation.
  This familiar layout is the starting point because it is what users expect;
  how Bruce's interface stands apart is decided later. While a reply has nothing
  to show yet (the prompt is being read, a skill is running), the chat shows a
  spinner and "Bruce is working…". A reply's tool-call markup is never shown, not
  even while it is being written (owner, 2026-10-02). A microphone button fills
  the message box by speech, through Android's on-device recogniser only; it is
  not shown on phones without one. `in progress`
- **Personalities** — Settings offers personalities, one choice for every chat:
  **Bruce** (fast, energetic, confident, warm, a little silly; the default) and
  **Milo** (curious, observant, calm, thoughtful). Each has fixed personality
  rules, written by the owner, placed ahead of the rest of the system prompt; the
  chat shows the chosen name (title, "Milo is thinking…", greetings) while the
  app itself stays Bruce. A change applies from the next reply. `done`
- **Formatted replies** — Replies are rendered as Markdown (headings, emphasis,
  lists, code, tables, quotes) in Bruce's own theme, parsed by commonmark-java and
  drawn in Compose, with no web view. Links show their full address and open in
  the browser only after the user confirms. Images show their description and
  address, and load only when the user taps Load image and the network mode
  allows the site (Any site; Approved sites for approved domains, once there is
  a list of them). `done`
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
- **Recommended models by device type** — Models Bruce has measured and found
  good are marked recommended, per device type: by the phone's memory size at
  first (4 GB, 8 GB and 12 GB tiers, one test phone each), and by processor type:
  whether the CPU has the dot-product instructions llama.cpp's fast paths need
  (owner, 2026-10-01). The bins come from an
  evaluation of the most popular small chat models (under 1B and under 3B
  parameters) on the three test phones: replies to a fixed set of prompts with
  skills on and off, at several context lengths, with speed and memory. Models
  are screened on the development machine first; only those that pass go to the
  phones. The setup wizard's model step suggests a few of the recommended models
  for this phone, each with its size and a line or two on what it is good at,
  downloadable in one tap; the user can skip the suggestions and
  pick their own from the model browser or import a file (owner, 2026-10-01).
  Bins (TASK-106, rule in `research/experiments/model-bins-2026-09-30/bins.py`):
  Recommended needs 80% of plain and skill cases, 8 tokens a second and a
  follow-up answer within 10 s; a model that invents answers with skills off is
  still Recommended for use with skills on, but never binned "skills off". 8 GB
  and 12 GB phones: Gemma 4 E2B, MiniCPM5 2B and Qwen3 1.7B. 4 GB phones without
  dot-product instructions have no Recommended model; Qwen3 1.7B Q4_K_M is the
  usable one and is suggested as slow. The model browser marks recommended
  models for the phone's tier and lists them first (TASK-107). `done`
  (TASK-103 to TASK-108)
- **Saved system prompt** — The first reply after a model loads has to read the
  fixed part of every prompt (system prompt and skill descriptions) first: about
  70 seconds for a 1.5B model on the XZ Premium. Bruce saves that evaluated part
  for each model on the phone and loads it instead, so the wait happens once, not
  each time the model loads. Kept in app storage, replaced when the prompt changes,
  and removed with the model or by clearing data. Saved only when the fixed part
  holds nothing about the user (no memories, summaries or file names). `done` (TASK-109)
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
  After the model step, a context size step offers only the sizes the phone and
  the chosen model can run, up to the model's trained maximum, with Auto selected.
  It is skipped when no model was installed or started downloading (owner,
  2026-10-02). `done` (TASK-111)
  A skills step then asks which kinds of skills to turn on (grouped by what they
  do: information, files, personal data, sensors, communication, phone settings,
  web), and warns when the system prompt with those skills would take more than
  25% of the chosen context. This replaces the earlier decision that the wizard
  sets no skills (owner, 2026-10-02). `done` (TASK-112)
- **Skills screen** — Reached from Settings. Lists every skill with what it does,
  a high-risk flag where it applies, and its state (Declined, Ask, Accepted),
  which the user changes there. Declined skills are not offered to the model.
- **Permissions screen** — Reached from Settings. Lists the files and folders
  Bruce has been granted and the Android permissions it holds. Android
  permissions are changed in Android's own settings, opened from each row;
  install-time ones (internet, network status) are shown as always granted, with
  the internet row pointing to Network mode.
- **Main screens** — Chat, Conversations, Models, Model browser, Downloads,
  Skills, Permissions (reached from Settings), Memory, Settings. `done`
  (downloads show in the model browser rather than a screen of their own)
- **Confirmation UI** — Shows the exact operation and its targets before
  approval. `done`
- **Response notifications** — When a reply finishes while Bruce is not on
  screen, a notification says so; tapping it opens the chat. Generation continues
  while the app is in the background. Needs the notification permission,
  requested when first needed. `done`
- **Voice** — Speech-to-text, text-to-speech, voice conversations; local where
  practical. `done` (TASK-074, TASK-080, TASK-081)
- **Vision** — Local multimodal models for images, documents, screenshots.
  `done` (images, TASK-082 and TASK-083; PDF and text documents, TASK-084; screenshots come through the photo picker)
- **Advanced automation and background workflows** — Multi-step and multi-file tasks,
  scheduled tasks, reminders, within Android background limits. `in progress`
  (reminders, TASK-094; scheduled tasks, TASK-095; multi-step multi-file tasks not planned as tasks yet)
- **Bruce Plus purchase** — One-time Google Play purchase unlocking a paid
  tier. Dropped: every feature is free. `dropped`
- **Data ownership controls** — Export and delete conversations, delete memories
  and models, remove permissions, clear local data, disable networking.
  `done`
- **Theme** — Material 3 theme with a Bruce brand palette (tan and white).
  User setting: System (default), Light, Dark. On Android 12 and later the user
  may turn on wallpaper-based dynamic colour; it is off by default.
  `done`
- **Default assistant** — Bruce can be chosen as Android's digital assistant app
  and opens from the system assistant gesture or button. No always-on wake word:
  low-power hotword detection is limited to privileged system apps.
  `done` (TASK-099; the role cannot be requested, so the user picks Bruce in Android's default apps)
- **App logo** — Flat, 2D, stylised reddish-tan working Cocker Spaniel with a white
  stripe down the middle of the head, three-quarter pose looking from left to right,
  with neck and collar. Master SVG in `docs/branding/`. Launcher icon has a
  transparent background and a themed monochrome layer; the Play icon sits on warm
  cream. `done`
- **Store listing** — Play Store feature graphic, listing text (name, short
  description, full description) and prepared answers for the Play Console setup
  questions, kept as Markdown in the repository. Phone screenshots follow once
  the product UI exists (Phase 1). `in progress` (feature graphic done, TASK-015)
- **Privacy policy** — Public privacy policy describing Bruce's local-only data
  handling, required by Google Play. Published on the owner's website; the
  contact email is the one given in `docs/store/`. `in progress` (written,
  `docs/store/privacy-policy.md`; not yet published)
- **Future surfaces** — Widget, Quick Settings tile, share-sheet actions,
  shortcuts. `done` (share to Bruce, TASK-100; tile, widget and shortcuts, TASK-101)

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
| Tool-call formats | llama.cpp's chat, template and parser sources from `common`, built by Bruce's own CMake target; not its HTTP or download code | Each model's own tool format (ADR 0001) without native network code outside the network mode. |
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
| Android platform APIs | Battery, device, storage, network, SAF, calendar, settings, and other skills | Yes, per skill and per user grant. |
| Remote model servers | Replies from a model on the user's llama.cpp (or other OpenAI-compatible) server | No. Only if the user adds one. |
| Brave Search API | The web search skill, with the user's own key | No. Only if the user adds a key and allows the network. |
| Wikipedia (MediaWiki REST API) | The web search skill's keyless provider (TASK-113) | No. Only if the user allows the network. |
| LangSearch API | The web search skill, with the user's own free key (TASK-113) | No. Only if the user adds a key and allows the network. |
| Web sites | The web page skill, GET only | No. Only where the network mode allows the site. |

## Constraints

- Inference must work with no network connection; a remote server is an optional alternative, never required.
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
| Dependency injection approach (Hilt or manual). | Developer, with user approval |
| Whether any crash reporting or telemetry exists. The privacy model implies none. | User |
| How Bruce's interface should differ from mainstream chatbots, once the chat interface exists. | User |
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
| 2026-10-02 | Changed | The context bar is always coloured green to red; the traffic-light setting is replaced by "Show context window", off by default (owner). |
| 2026-10-02 | Changed | Recommended models settled: the bins rule, Gemma 4 E2B, MiniCPM5 2B and Qwen3 1.7B for 8 and 12 GB, Qwen3 1.7B Q4_K_M as slow but usable for 4 GB; the browser marks and ranks them (owner delegated, TASK-106, TASK-107). |
| 2026-10-02 | Added | Free search providers: Wikipedia (no key) and LangSearch (free key) alongside Brave (owner) (TASK-113). |
| 2026-10-02 | Changed | Setup wizard asks which kinds of skills to turn on, with a warning when the system prompt takes over 25% of the context; reverses "no skill toggles" of 2026-09-28 (owner) (TASK-112). |
| 2026-10-02 | Added | Setup wizard context size step; Auto context keeps the model within 6 GB (16K at most for larger models) (owner) (TASK-111). |
| 2026-10-02 | Changed | Chat shows that Bruce is working, and never shows tool-call markup while a reply streams (owner). |
| 2026-10-01 | Added | Thinking off for every model that can switch it off, with a setting to turn it on; models that always think keep thinking (owner) (TASK-110). |
| 2026-10-01 | Changed | Setup wizard's model step suggests recommended models for the phone; the user can skip them and choose their own (owner) (TASK-107). |
| 2026-10-01 | Added | Saved system prompt: the evaluated fixed prompt is kept per model, so the first reply after loading does not re-read it (owner) (TASK-109). |
| 2026-10-01 | Changed | Recommended models are binned by memory and by processor type (dot-product instructions or not) (owner). |
| 2026-10-01 | Changed | Inference threads default to four (every core on phones with fewer), changeable in Settings (owner). |
| 2026-09-30 | Changed | Model evaluation screens models on the development machine first; only those that pass are run on the phones (owner) (TASK-108). |
| 2026-09-30 | Added | Recommended models by device type (memory tiers), from an evaluation of popular small models on the three test phones (owner) (TASK-103 to TASK-107). |
| 2026-09-30 | Changed | Model browser's Task filter: Any (now the default), Text generation, Image and text (owner). |
| 2026-09-30 | Changed | A model without GGUF files now points to its GGUF copies; a repository's name or address shows that repository (TASK-102). |
| 2026-09-30 | Added | Hugging Face discovery: a model without GGUF files points to its GGUF copies (owner; TASK-102). |
| 2026-09-30 | Changed | Quick Settings tile, home-screen widget (buttons only, owner) and launcher shortcuts done (TASK-101). |
| 2026-09-30 | Changed | Share to Bruce done: text, links, images and PDFs start a new chat as attachments (TASK-100). |
| 2026-09-30 | Changed | Default assistant done through an ACTION_ASSIST activity; Android's requirements recorded (research/sources/android-assistant-role-2026-09-30) (TASK-099). |
| 2026-09-30 | Changed | Model profiles done: named models picked per chat, restored when the chat opens; no automatic routing (owner) (TASK-098). |
| 2026-09-30 | Changed | Search my files skill done: the best passages of granted files, offered once something is indexed (TASK-097). |
| 2026-09-30 | Changed | Local file index done: EmbeddingGemma-300M chosen from measurements (research/experiments/embedding-models-2026-09-30), downloaded on request, updated with grants and daily while charging (TASK-096). |
| 2026-09-30 | Changed | Scheduled tasks done with WorkManager (owner): once, daily or weekly; phone models only while charging; unattended runs never use skills that need approval (TASK-095). |
| 2026-09-30 | Changed | Reminders done: exact alarms where allowed (offered once), otherwise inexact and said so; listed and cancelled in Settings, Reminders; restored after restarts (TASK-094). |
| 2026-09-30 | Changed | Camera skill done: opens the camera, the user takes the photo, it goes with their next message; no microphone skill (owner) (TASK-093). |
| 2026-09-30 | Changed | Contacts skill done: find by name, only the detail asked for (TASK-092). |
| 2026-09-30 | Changed | Location skill done: one reading while Bruce is on screen, approximate unless precise is allowed, never tracking (TASK-091). |
| 2026-09-30 | Changed | Notifications skill: Bruce posts a note the user asked for; it never reads other apps' notifications (owner) (TASK-090). |
| 2026-09-30 | Changed | Texts and calls: Bruce opens the messaging or phone app prefilled and the user sends or calls; no SMS or call permissions (owner) (TASK-089). |
| 2026-09-30 | Changed | Copy-to-clipboard skill done, write only, off by default (TASK-088). |
| 2026-09-30 | Changed | Share text, open a web page, open an app skills done, off by default (TASK-087). |
| 2026-09-30 | Changed | Calendar skills: list events, and create, change or delete one with an approval that shows the exact change every time (TASK-086). Skills needing an Android permission ask for it when turned on. |
| 2026-09-30 | Changed | Rename and move file skills done; an approval binds both the file and where it goes (TASK-085). |
| 2026-09-30 | Changed | Documents in chat: PDF and text files read on the phone (PdfBox-Android without BouncyCastle; encrypted PDFs refused, owner's choice after its advisories) (TASK-084). |
| 2026-09-30 | Changed | Images in chat: photos and screenshots go to vision models on the phone (llama.cpp mtmd) or to a server that can see them (TASK-082). |
| 2026-09-30 | Changed | Vision models download with their image projector, counted in the memory estimate (TASK-083); images in chat follow (TASK-082). |
| 2026-09-30 | Changed | Voice conversations done: long-press the microphone to talk hands-free (TASK-081). |
| 2026-09-30 | Changed | Replies can be read aloud with the phone's text-to-speech, an offline voice preferred (TASK-080). |
| 2026-09-30 | Changed | Hugging Face sign-in with a pasted access token done; write tokens are refused (TASK-079). |
| 2026-09-30 | Changed | Conversation export done: one chat or all as Markdown or JSON (TASK-078). |
| 2026-09-30 | Changed | Owner's decisions for TASK-078 to TASK-101: export as Markdown and JSON; default assistant built now; embedding model chosen from evidence; texts and calls open the app prefilled; scheduled phone-model runs only while charging; profiles per chat without automatic routing. |
| 2026-09-30 | Changed | Statuses brought up to date (model manager, context, memory, confirmations, denials, untrusted content, Android settings, notifications, screens, hardware tuning marked done); tasks created for the features not yet started (TASK-078 to TASK-101). |
| 2026-09-29 | Changed | Remote model servers done: chat through a server's model, chosen from the Servers tab or the switcher (TASK-076, TASK-077). |
| 2026-09-29 | Changed | Remote model servers can be added and checked (TASK-075); chatting through one follows. |
| 2026-09-29 | Changed | Web search done with Brave's LLM Context endpoint; results saved with the chat (owner's choice, against a storage clause in Brave's terms) (TASK-070). |
| 2026-09-29 | Changed | Web pages skill done (TASK-069). |
| 2026-09-29 | Changed | Approved sites done (TASK-068). |
| 2026-09-29 | Added | Remote model servers (owner): OpenAI-compatible API, HTTPS or plain HTTP on the local network, a server is its own permission, listed with installed models (TASK-075 to TASK-077). |
| 2026-09-29 | Changed | Downloads are no longer blocked by the size-only fit (owner): Download reads the file's header first and stops only if the model really cannot fit. Gemma 4's memory is estimated as llama.cpp uses it. |
| 2026-09-29 | Changed | Voice input done, on-device only (TASK-074). |
| 2026-09-29 | Changed | The default context size is Auto: the biggest that leaves the phone room for other apps, within what the model was trained for (TASK-073 done). |
| 2026-09-29 | Changed | Models show their expected RAM against the phone's (TASK-072 done). |
| 2026-09-29 | Added | Voice input through Android's on-device recogniser only; RAM use per model with a bar against the phone's RAM; context sizes up to 64K; a default context picked from the phone and the model. |
| 2026-09-29 | Changed | Skill defaults (owner): settings, battery, device and storage skills start off; file skills are locked off until something is granted, and network status while the network mode is Offline. Locked skills show why on the Skills screen and are never offered. |
| 2026-09-29 | Changed | Tool definitions reach the model on one line instead of indented, and three skill descriptions are shorter (owner): the Pixel's empty-chat prompt with Llama 3.2 1B drops by about a fifth, measured with no loss of tool-call accuracy. |
| 2026-09-29 | Changed | Changing Android settings done: brightness, screen timeout, auto-rotate, vibrate on touch and volumes, asking each time; other settings open their Android page. |
| 2026-09-29 | Changed | Finding and reading Android settings done. |
| 2026-09-29 | Added | Android settings skills (search, list, read; change the few Android allows, asking first; otherwise open the settings page), approved sites with in-chat approval, web pages (GET only) and web search (Brave Search API, user's own key). Search provider decided. |
| 2026-09-29 | Changed | Vulkan is not offered on PowerVR GPUs (owner, for now): their driver gives wrong output and can crash the GPU on the Pixel 11. With the CPU as the only backend, Auto is not offered. |
| 2026-09-29 | Changed | Formatted replies done. Images in replies load only when tapped, even in Any site mode: an image address written by the model could carry chat text to any server. |
| 2026-09-29 | Changed | Folder instructions done: reviewed on the Permissions screen when a folder is granted or they change; followed ones go to the model once per chat with the first file result there. |
| 2026-09-29 | Changed | Reply notifications done: replies finish off screen in a short foreground service and are announced; tapping opens the chat. |
| 2026-09-29 | Changed | Memory done: off by default, per model or shared; facts found by a pass after each reply, recalled at a chat's start, reviewed and deleted in Settings. |
| 2026-09-29 | Changed | Tool templates for stripped copies are fetched from another Hub copy of the same model, not shipped with Bruce (licence). |
| 2026-09-29 | Changed | Installed models whose template has no tool support say so, with a search for a copy that has it. |
| 2026-09-29 | Changed | The model browser marks copies whose chat template has no tool support ("Limited skill use") and lists them after the others. |
| 2026-09-29 | Added | Context traffic lights: the context bar is green, amber or red by how full the chat is; Appearance setting, on by default. |
| 2026-09-29 | Removed | Short skill list: measured, saves about 10% of skill tokens and loses accuracy; full skill definitions stay. |
| 2026-09-29 | Changed | Settings and Models warn when a context length leaves the chat less than a quarter of it. |
| 2026-09-29 | Changed | File skills are offered only once a file or folder is granted: 364 fewer prompt tokens by default. |
| 2026-09-29 | Added | Model files whose chat template cannot do tool calls are marked in the browser and on installed models; official tool templates for known families (licence permitting). |
| 2026-09-29 | Changed | The evaluated prompt is reused between turns and skill steps; later turns take a half to a third of the time. |
| 2026-09-29 | Changed | Skill use is no longer shown in the chat (owner: people want the answer); approval cards still show. It is still saved with the chat. |
| 2026-09-29 | Changed | TASK-056 (reuse the evaluated prompt) moved ahead of TASK-059, TASK-060 and TASK-054: replies with skills were very slow. |
| 2026-09-28 | Added | File skills offered only once a file or folder is granted; a warning when a context length leaves little room for the chat. |
| 2026-09-28 | Changed | Memory decided: facts found by an extra model pass after replies; up to 10 relevant facts per prompt. Short skill list moved ahead of memory. |
| 2026-09-28 | Changed | Auto-summarise done: off by default, threshold 85-100% (default 90%), summary shown as a marked note. |
| 2026-09-28 | Changed | Context indicator and drop-oldest overflow done. |
| 2026-09-28 | Changed | File delete skill done: one file inside a granted folder, high risk, asked every time by default. |
| 2026-09-28 | Changed | File create and write skills done: create_file and write_file, plain text only, asked every time by default. |
| 2026-09-28 | Changed | File read skills done: list_files and read_file in granted folders, plain text only, off by default. |
| 2026-09-28 | Changed | Exact-operation confirmation done: approval card in chat, bound to the request, targets and policy version, expiring after 15 minutes. |
| 2026-09-28 | Changed | File and folder grants done: added and revoked in Permissions; the model names targets as `<grant name>/<path>`. |
| 2026-09-28 | Changed | Skills screen done: per-skill Declined, Ask or Accepted in Settings > Skills, with the high-risk warning. |
| 2026-09-28 | Changed | Permissions screen done: Android permissions with a link to change each in Android settings; files and folders section empty until TASK-040. |
| 2026-09-28 | Changed | Sidekick wording and light dog puns in loading and empty states done. |
| 2026-09-28 | Changed | Personalities done: Bruce and Milo in Settings > Sidekick. |
| 2026-09-28 | Changed | Tool calling works in chat: skill uses shown inline and saved with the chat; agent loop done. |
| 2026-09-28 | Changed | Automatic skills done (date and time, calculator, battery, device, storage, network). |
| 2026-09-28 | Added | Personalities (Bruce default, Milo) chosen in Settings; user-facing text calls Bruce a sidekick; light dog puns in small UI copy, replies, README and store text. |
| 2026-09-28 | Changed | Skill states and policy engine done; states stored in their own database with a policy version; Clear all data resets them. |
| 2026-09-28 | Added | Formatted replies: Markdown rendered natively; links confirmed before opening; images only where the network mode allows. |
| 2026-09-28 | Changed | Tool-calling format decided (ADR 0001): each model's own format via a narrow build of llama.cpp's chat code, Bruce's format with a grammar as fallback; full skill descriptions until a prompt budget. Open question closed. |
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
