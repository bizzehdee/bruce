# Google Play listing (TASK-017)

Text for the Play Console store listing. Limits: name 30 characters, short description 80,
full description 4,000. Counts are checked by `python3 docs/store/check-listing.py`.

## App name

Bruce: Your AI Sidekick

## Short description

A private AI sidekick that runs on your phone. No ads, no account, no cloud.

## Full description

Bruce is an AI sidekick that runs on your phone. You choose an open model, Bruce downloads it once, and from then on your chats stay on the phone. Bruce works offline. There are no ads, no subscription, no purchases and no account.

Your model, your choice
• Bruce suggests models tested on phones like yours, with a line on what each is good at, and downloads your pick in one tap.
• Or browse thousands of models on Hugging Face, ranked by whether they fit your phone, or import your own GGUF file.
• Run models on your own llama.cpp or OpenAI-compatible server at home instead, if you have one.

A sidekick, not just a chat
Bruce can use skills you switch on: check the time, battery or storage, do sums, read and write files in folders you grant, look up a contact, list or add calendar events, find your location once, set reminders, open apps and web pages, search Wikipedia or the web, and search the text of your own files. Every skill is off or asks first unless you choose otherwise, and Bruce shows exactly what it will do before it changes anything. The model can never change your permissions.

Private by design
• Chats, memories and settings stay in Bruce's private storage on your phone and are excluded from cloud backup.
• Bruce connects to the internet only for features you turn on, starting in Offline mode.
• Voice input uses your phone's on-device speech recogniser; the audio never leaves the phone.
• No analytics, no crash reporting, no tracking. The developer runs no server and receives none of your data.

Made for small phones too
Bruce measures what your phone can run, picks a context size that fits, and keeps its working memory saved so the first reply after loading a model comes quickly. A setup wizard walks you through the network mode, a first model, the context size and which kinds of skills to turn on.

More to sniff out
• Talk to Bruce, or have replies read aloud.
• Show it a photo or a PDF, with a model that supports it.
• Pick a personality: Bruce, quick and eager, or Milo, calm and thoughtful.
• Optional memory of facts you tell it, which you can review and delete.
• Share text, links, photos or PDFs to Bruce from any app.
• Export your chats as Markdown or JSON.

Bruce is free and open source under the GPL-3.0-or-later licence.

Good to know: Bruce needs a 64-bit ARM phone with Android 10 or later. Models are large downloads (1.3–2.5 GB for the suggested ones) and need free memory; speed depends on your phone. AI models can be wrong, so check anything important.
