# Android's assistant role (for TASK-099)

Retrieved 2026-09-30 with curl: `roles.xml` from
https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/res/xml/roles.xml

- `android.app.role.ASSISTANT` is `requestable="false"`: an app cannot ask for it through
  RoleManager; the user picks it in Settings, Default apps, Digital assistant app. `showNone`, so
  the user can also choose none.
- Qualifying apps (AssistantRoleBehavior): a VoiceInteractionService with supportsAssist, or an
  activity handling `android.intent.action.ASSIST`. Bruce uses the activity: the assist gesture then
  opens it.
- The role grants the `sms` permission set, READ_CALL_LOG and others to its holder, but only
  permissions the app declares. Bruce declares none of them, so holding the role gives it no data.
