# Implementation Plan - Fix Render Issue in Preview

The user is experiencing a rendering exception in the preview environment: `The Security Manager is deprecated and will be removed in a future release`. This error typically occurs when the Android Studio Layout Editor or Compose Preview encounters a fatal exception during inflation or rendering, especially when there are compatibility issues between the project's theme (Material 3) and the layout's components (Material Components/MDC).

## User Review Required

> [!IMPORTANT]
> The error message "Security Manager is deprecated" is often a generic wrapper for a deeper inflation error in modern Android Studio versions. I will be cleaning up the XML layout to ensure compatibility with Material 3 and adding a proper Compose Preview to verify the fix.

## Proposed Changes

### [UI Components]

#### [MODIFY] [activity_login.xml](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/res/layout/activity_login.xml)
- Clean up redundant `xmlns:app` declarations.
- Update MDC-specific attributes to their modern equivalents (e.g., `passwordToggleEnabled` -> `endIconMode`).
- Move the `xmlns:app` declaration to the root element.

#### [MODIFY] [LoginActivity.kt](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/java/com/training/monitor/ui/login/LoginActivity.kt)
- Add a `@Preview` Composable at the end of the file.
- Use `AndroidView` to wrap the `activity_login.xml` layout, allowing it to be viewed and debugged in the Compose Preview tab.
- Wrap the preview in the project's `Theme.TrainingMonitor` to ensure correct rendering.

## Verification Plan

### Manual Verification
- I will use the `render_compose_preview` tool to verify that the new `@Preview` in `LoginActivity.kt` renders without the "Security Manager" exception.
- I will check the UI hierarchy to ensure all elements (EditTexts, Button) are correctly inflated.
