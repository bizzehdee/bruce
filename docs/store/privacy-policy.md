# Bruce privacy policy

Effective date: 1 October 2026

Bruce is an AI sidekick for Android. It runs an AI model on your phone. This
policy explains what Bruce stores, what leaves your phone, and how you delete
your data.

## Summary

- Bruce has no accounts, no ads, no analytics and no crash reporting.
- The developer of Bruce runs no server and receives none of your data.
- Your chats, memories, settings and model files stay on your phone.
- Bruce connects to the internet only for features you turn on. A new
  installation starts in Offline mode, which blocks every connection.

## Data stored on your phone

Bruce stores the following data in its private app storage on your phone:

- Your conversations, including messages, attached images and documents, and
  the results of skills used in them.
- Memories: facts Bruce saves across chats, when you turn Memory on. Memory is
  off by default.
- Model files that you download or import.
- A saved copy of the fixed start of the prompt for each model, so that the
  first reply after loading a model is faster. Bruce saves this copy only when
  the prompt holds no memories, no chat summaries and no names of your files or
  folders.
- Settings, skill states and the files and folders you grant Bruce access to.
- Reminders and scheduled tasks that you create.
- An index of the files you grant to the file search skill, when you set up file
  search.
- Your Hugging Face sign-in token and your Brave Search API key, if you add them.
  Bruce encrypts both with a key held in the Android Keystore.

Bruce excludes all of this data from Android cloud backup and from
device-to-device transfer. Other apps cannot read Bruce's private storage. Bruce
relies on your phone's storage encryption and does not encrypt its database a
second time.

## What leaves your phone

Bruce sends data off your phone only in the cases below. Each case needs a
network mode that allows it. You choose the network mode in Settings: Offline,
Hugging Face only, Approved sites, or Any site.

| Feature | Data sent | Sent to |
|---|---|---|
| Find and download models | Your search text, the filters you choose, a largest model size worked out from your phone's free memory, and requests for model files and their headers | Hugging Face |
| Download the file search model | A request for the model file | Hugging Face |
| Sign in with Hugging Face | The sign-in, through your web browser | Hugging Face |
| Web search skill | The search text the model writes for your request, and your API key | Brave Search |
| Web page skill | A request for the page address | The web site |
| Load an image in a reply | A request for the image address, after you tap Load image | The web site |
| Remote model server | The whole conversation sent to the model, including skill results, images and memories | The server you add |

Every request sends your phone's IP address and the text "Bruce" with the app
version. Bruce does not send your phone's model name, its memory size or any
identifier. Each service handles the data under its own privacy policy:

- Hugging Face: <https://huggingface.co/privacy>
- Brave Search: <https://search.brave.com/help/privacy-policy>
- A web site or a remote server you add: the policy of whoever runs it.

If you use a remote model server, the conversation goes to that server. This
includes data from skills, such as a contact or your location, when a skill
returns it in that conversation. Use only servers you trust.

## Phone permissions and skills

Skills let the model act for you. Each skill has a state that you set in the
Skills screen: Declined, Ask or Accepted. Skills that read personal data start
as Declined. Android also asks for its own permission the first time a skill
needs one. The model cannot change skill states, permissions or file grants.

| Permission | Used for |
|---|---|
| Contacts | The contacts skill: find a contact you ask about |
| Location | The location skill: your location when you ask for it |
| Calendar | The calendar skills: read, add and change events |
| Microphone | Voice input, with Android's on-device speech recogniser only |
| Notifications | Telling you that a reply or a reminder is ready |
| Alarms and starting after a restart | Reminders and scheduled tasks |
| Modify system settings | Changing a setting, such as brightness, after you confirm |
| Internet and network status | The features in the table above |

Bruce does not hold the camera permission. The camera skill opens your phone's
camera app, and Bruce receives only a photo that you take there.

Data that a skill reads goes to the model on your phone and is stored in that
conversation. It leaves the phone only through a remote model server, as
described above.

Reading replies aloud uses your phone's text-to-speech engine. Bruce prefers an
offline voice. If your phone has only a network voice for your language, Bruce
tells you, and the engine's provider handles that text.

## Deleting your data

- Delete one or more conversations from the chat list.
- Delete memories in the Memory screen.
- Delete a model in the Models screen. Its saved prompt copy is deleted with it.
- Remove file and folder grants in the Permissions screen.
- Sign out of Hugging Face, or remove your Brave Search key, in Settings.
- Delete all of Bruce's data with Settings, Clear all data.
- Uninstall Bruce. Android then deletes all of Bruce's data from your phone.

Bruce sends no data to the developer, so the developer has no copy to delete.

## Children

Bruce is not directed at children under 13.

## Changes to this policy

A changed policy is published at the same address with a new effective date.
The source of this policy is kept with Bruce's source code.

## Contact

Questions about this policy: dhorrocks@gmail.com
