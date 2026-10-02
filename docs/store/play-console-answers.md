# Play Console setup answers (TASK-017)

Prepared answers for the Play Console's app setup questions, checked against the app and
`privacy-policy.md` on 2026-10-02. Google's guidance used is archived in
`research/sources/google-play-policy-2026-10-02/`.

## Blocking issue: in-app reporting of AI content

Google Play's AI-Generated Content policy covers "Text-to-text AI chatbot apps, in which the AI
generated chatbot interaction is a central feature of the app", and says: "Apps that generate
content using AI must contain in-app user reporting or flagging features that allow users to report
or flag offensive content to developers without needing to exit the app." Bruce has no such feature,
and the developer runs no server to receive reports. Bruce cannot be published on Google Play until
the owner decides how reports reach the developer (TASK-114). F-Droid and GitHub Releases are not
affected.

## App or game, free or paid

- App or game: App.
- Category: Productivity.
- Free or paid: Free. No in-app products or subscriptions.
- Contains ads: No.

## App access

All functionality is available without special access: no account, no sign-in, no paid content.
Instructions for reviewers: on the setup wizard's Network step choose "Hugging Face only", then on the
model step tap Download on a suggested model (1.3–2.5 GB) and wait for it to finish; the chat then
works offline. Hugging Face sign-in is optional and only needed for gated models.

## Content rating questionnaire

- Category: All other app types (not a game, not a social or communication app).
- Violence, fear, sexuality, gambling, drugs, crude humour: the app's own content contains none.
- Users can interact or exchange content: No. There is no account, chat between users or sharing of
  content with other users of Bruce.
- Shares the user's location with other users: No.
- Allows purchases of digital goods: No.
- Unrestricted internet access: Yes, optionally: in the Any site network mode the web page skill can
  read any web page the model asks for, and links open in the browser after the user confirms.
- Generative AI: Yes. Replies are written by an open model that the user chooses and downloads from
  Hugging Face; the developer does not train or filter the models. Declare that AI can generate
  content that is not reviewed.

## Target audience

- Target age groups: 18 and over. LangSearch's terms require users to be 18 or have a guardian's
  consent, and models are unfiltered; the privacy policy says Bruce is not directed at children
  under 13.
- Appeals to children: No.

## Data safety form

Google defines "collect" as "transmitting data from your app off a user's device", whoever receives
it, and lets transfers made by "a specific user-initiated action, where the user reasonably expects
the data to be shared" go undeclared as sharing. Every transfer below is started by the user
turning a feature on, so data is declared as collected (optional) and not shared.

- Does the app collect or share any required user data types: Yes.
- Is all user data encrypted in transit: No. Hugging Face, LangSearch, Brave, Wikipedia and HTTPS
  sites are encrypted, but the user's own model server may use plain HTTP on the local network.
- Can users request that their data is deleted: No request is needed: the developer holds no data.
  Users delete everything in the app (Settings, Clear all data) or by uninstalling.

| Data type | Collected | Why it leaves the phone | Optional | Purpose |
|---|---|---|---|---|
| App activity: In-app search history | Yes | Model searches go to Hugging Face; web searches go to LangSearch, Brave or Wikipedia | Yes | App functionality |
| App activity: Other user-generated content | Yes | Chats go to the user's own model server, when one is chosen | Yes | App functionality |
| Messages: Other in-app messages | No | Bruce has no messaging between users | – | – |
| Location: Approximate and precise | Yes | Only as a skill result inside a chat sent to the user's own model server | Yes | App functionality |
| Personal info: Name, email address, phone number | Yes | A contact the user asks about, inside a chat sent to the user's own model server | Yes | App functionality |
| Calendar: Calendar events | Yes | Events the user asks about, inside a chat sent to the user's own model server | Yes | App functionality |
| Photos and videos: Photos | Yes | Photos the user attaches, sent with a chat to the user's own model server | Yes | App functionality |
| App activity: Installed apps | Yes | App names the open-app skill lists, inside a chat sent to the user's own model server | Yes | App functionality |
| Files and docs | Yes | File text the user asks about, inside a chat sent to the user's own model server | Yes | App functionality |
| Audio | No | Speech recognition runs on the phone | – | – |
| Device or other IDs | No | No identifiers are sent | – | – |
| App info and performance: Crash logs, Diagnostics | No | No crash reporting or analytics | – | – |
| Financial info, Health and fitness, Contacts list, Web browsing history | No | – | – | – |

Owner to confirm: whether to declare the remote-server rows at all. They arise only when the user
adds their own server, which is arguably the user sending data to themselves.

## Permissions declarations

- `SCHEDULE_EXACT_ALARM`: used for reminders the user sets, which must fire on time; Bruce asks once
  and falls back to inexact alarms when refused. Declare as "Calendar or alarm functionality".
- `WRITE_SETTINGS` (Modify system settings): changes brightness, screen timeout, auto-rotate and
  sound levels when the user asks and confirms; granted by the user in Android's settings.
- `FOREGROUND_SERVICE`: keeps a reply generating while Bruce is in the background, as a
  `shortService` (a few minutes at most). Whether the Play Console asks for a foreground service
  declaration for this type was not checked; answer it if asked.
- Location, contacts, calendar, microphone: runtime permissions asked only when a skill the user
  turned on first needs them.
- No `QUERY_ALL_PACKAGES`, SMS, call log, accessibility or background location permissions. Package
  visibility is limited by `<queries>` to launcher apps (for opening an app by name), messaging and
  phone apps, speech recognition and text-to-speech services.
