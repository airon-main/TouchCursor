# Changelog

## 0.4.0

- removed obsolete immersive-navigation workaround and accessibility UI-event subscription;
- cached display bounds instead of querying WindowManager every movement tick;
- made gesture cancellation yield and restart lazily instead of retrying immediately;
- cached movement speed in the service hot path;
- stopped reapplying AccessibilityServiceInfo on unrelated settings changes;
- made cursor-size dragging preview live but persist only when the adjustment finishes;
- made external broadcast control opt-in and component-disabled by default;
- disabled Android backup for app preferences;
- enabled R8/resource shrinking for release builds;
- moved target SDK to 35 and added an adaptive launcher icon;
- added privacy documentation and CI lint validation.
