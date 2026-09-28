# The chat's keyboard gap: panning plus double insets

Observed 2026-09-29 on the Xperia 1 II (Android 12) and reported on the Pixel 11.

Tapping the chat input left a keyboard-sized gap above the keyboard and pushed the top bar off
screen. `dumpsys window windows` showed Bruce's window with `sim={adjust=pan}`: with no
`windowSoftInputMode`, Android panned the whole window while Compose's `imePadding` also lifted
the input. After `android:windowSoftInputMode="adjustResize"` (`sim={adjust=resize}`) the pan
stopped, but a navigation-bar-sized gap remained: the Scaffold's content padding already includes
the navigation bar, and `imePadding` counts it again. `consumeWindowInsets(padding)` before
`imePadding()` removed it.

Read when a screen with a text field shows a gap above the keyboard or jumps when it opens.
