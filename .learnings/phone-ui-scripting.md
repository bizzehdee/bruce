# Scripting the chat UI on the test phones

Established: 2026-09-30 (Xperia 1 II, Pixel 11).

- An open keyboard catches taps and swipes meant for the app: a swipe over it gesture-types
  words into the message box ("by" appeared on the Xperia 1 II). Close it before scrolling.
- Check `dumpsys input_method` for `mInputShown=true` before closing it; a close key sent with
  no keyboard up acts as Back and leaves the screen.
- `input keyevent 111` (Escape) closes the keyboard on the Pixel 11 but not on the Xperia 1 II
  (Android 12); `input keyevent 4` (Back) does, while the keyboard is shown.
- The chat list scrolls to the last message's top, so buttons under a long reply are off screen
  until the list is scrolled; uiautomator lists only what is on screen.
