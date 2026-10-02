# Google Play policies for the store listing (TASK-017)

Retrieved 2026-10-02 with curl.

| File | Page |
|---|---|
| `ai-generated-content-policy.html` | AI-Generated Content policy, https://support.google.com/googleplay/android-developer/answer/13985936 |
| `ai-generated-content.html` | Understanding the AI-Generated Content policy, https://support.google.com/googleplay/android-developer/answer/14094294 |
| `data-safety.html` | Data safety section guidance, https://support.google.com/googleplay/android-developer/answer/10787469 |
| `content-ratings.html` | Content ratings, https://support.google.com/googleplay/android-developer/answer/9898843 |
| `target-audience.html` | Target audience and app content, https://support.google.com/googleplay/android-developer/answer/9867159 |
| `store-listing-metadata.html` | Metadata policy, https://support.google.com/googleplay/android-developer/answer/9898842 |

Extract for Bruce:

- In scope of the AI-Generated Content policy: "Text-to-text AI chatbot apps, in which the AI
  generated chatbot interaction is a central feature of the app." Requirement: "Apps that generate
  content using AI must contain in-app user reporting or flagging features that allow users to
  report or flag offensive content to developers without needing to exit the app. Developers should
  utilize user reports to inform content filtering and moderation in their apps." Bruce has no such
  feature yet (TASK-114).
- Data safety: "'Collect' means transmitting data from your app off a user's device", including
  to third parties. Data processed only on the device need not be disclosed. Transfers made by "a
  specific user-initiated action, where the user reasonably expects the data to be shared" are not
  "sharing". Even an app that collects nothing must complete the form and link a privacy policy.
