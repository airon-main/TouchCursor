# Changelog

## 0.4.1

- normalized Android-rotated DPAD key codes back to physical directions and aligned pointer/touch coordinates to the actual overlay viewport, fixing landscape behavior;
- added a thin inverse-color outline to runtime cursors and UI previews;
- replaced the launcher artwork with an original vector KeyMouse mark;
- switched the project license from Apache-2.0 to MIT;
- removed the redundant `build.sh`;
- replaced the prerelease Gradle wrapper with stable Gradle 8.9 and made CI use the wrapper.

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
