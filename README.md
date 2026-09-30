# Bruce

Bruce is an AI sidekick for Android that runs entirely on your phone. Loyal, local, and never sniffing around your data.

- **Your model.** You choose which open-weight model Bruce uses. Download one from Hugging Face or import your own GGUF file.
- **Your phone.** The model runs on the device. Bruce works offline once a model is installed.
- **Or your own server.** If you run llama.cpp (or another OpenAI-compatible server) at home, Bruce can use its models instead. Your skills and permissions still stay on the phone.
- **Your permissions.** Bruce can only use the phone features and folders you allow. Bruce can read, create, change, rename, move and delete files there. Sensitive actions, such as deleting a file, always ask you first. Settings → Permissions shows everything Bruce holds on the phone. If a folder you grant has an `AGENTS.md` file for AI helpers, Bruce shows it to you and follows it only if you say so.
- **Where you are, if you allow it.** Off unless you turn it on: Bruce can check the phone's location once when a question needs it, approximate unless you choose precise. It never tracks you.
- **Your contacts, if you allow it.** Off unless you turn it on: Bruce can look up a person's number or email address by name, giving only the detail asked for.
- **Your calendar, if you allow it.** Off unless you turn it on: Bruce can list your events, and add, change or delete one, showing you exactly what will change first. Repeating events are left alone.
- **Hand things on.** If you turn them on, Bruce can open the share sheet with text it wrote, open a web page in your browser, open an app by name, copy text to the clipboard (it never reads the clipboard), get a text message or call ready for you to send or place yourself, or post a note to you as a notification. You always see what opens.
- **Talk to it.** Tap the microphone to speak a message. Speech is turned into text on the phone itself; the audio never leaves it. Tap the speaker under a reply to hear it read aloud, or long-press the microphone for a hands-free conversation.
- **Show it a picture.** With a model that can see (Bruce downloads its image support with it), add a photo or screenshot to a message. Images stay on the phone unless you use your own server's model.
- **Share a document.** Attach a PDF or text file to a message and ask about it. Bruce reads its text on the phone; password-protected PDFs and scans without text cannot be read.
- **Readable replies.** Headings, lists, tables and code show formatted. Links ask before opening, and images from the web load only when you tap them.
- **Your sidekick.** Pick a personality: Bruce, quick and eager, or Milo, calm and thoughtful.
- **Your memory, if you want it.** Off unless you turn it on: Bruce then keeps short facts from your chats on the phone, uses them in later chats, and shows you every one to delete.
- **Your chats to keep.** Export one chat or all of them as Markdown or JSON, to a file you choose.
- **Free.** Every feature is free. No ads, no subscription, no purchases, no account.

## Status

Bruce is in early development and cannot be installed yet. The current work is a technical prototype that tests on-device model performance.

## Requirements (planned)

- Android 10 or later.
- A 64-bit ARM phone.
- Enough free storage and memory for your chosen model.

## Licence

Bruce is free software, released under the [GNU General Public License v3.0 or later](LICENSE).
