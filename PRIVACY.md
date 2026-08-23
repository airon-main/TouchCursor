# Privacy

KeyMouse does not collect, transmit or sell user data.

- The app requests no Internet permission and contains no analytics or advertising SDKs.
- Bindings, cursor preferences and the external-control opt-in are stored locally in Android `SharedPreferences`.
- The Accessibility service does not retrieve or inspect window contents and does not subscribe to UI accessibility events. It uses Accessibility only to observe hardware key events, draw the cursor overlay and inject user-requested touch gestures.
- External broadcast control is disabled by default and must be enabled explicitly in the app.

If the project later adds networking, analytics or crash reporting, this document should be updated before release.
