# Privacy

KeyCursor does not collect, transmit or sell user data.

- The app requests no Internet permission and contains no analytics or advertising SDKs.
- Bindings, cursor preferences and the external-control opt-in are stored locally in Android `SharedPreferences`.
- The Accessibility service observes hardware key events, draws the cursor overlay and injects user-requested touch gestures.
- While **Scroll mode** is actively used, KeyCursor retrieves the active Accessibility node tree only to find a scrollable element under the cursor and inspect its bounds/supported scroll actions. It does not read, store or transmit node text or other UI content, and it does not subscribe to UI accessibility events.
- External broadcast control is disabled by default and must be enabled explicitly in the app.
