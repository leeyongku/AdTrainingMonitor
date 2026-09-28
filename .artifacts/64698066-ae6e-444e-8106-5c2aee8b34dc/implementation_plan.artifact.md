# Implementation Plan - Fix Render Issues in Login Activity

The user is experiencing a render issue in the "Compose Preview" (likely the layout preview for `activity_login.xml` or a Compose Preview intended for it). The error "The Security Manager is deprecated" is a JVM 17+ warning, while "Error inflating the preview" is the actual failure.

## User Review Required

> [!IMPORTANT]
> The project uses Material 3 theme but `activity_login.xml` uses Material 2 (MDC) styles and deprecated attributes. This mismatch, combined with fragmented namespace declarations, is likely causing the inflation error in the preview.

## Proposed Changes

### Layout Cleanup

#### [MODIFY] [activity_login.xml](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/res/layout/activity_login.xml)
- Move all namespace declarations (`xmlns:app`) to the root element.
- Add `xmlns:tools` and `tools:context` for better IDE integration.
- Update `TextInputLayout` styles from Material 2 (`Widget.MaterialComponents`) to Material 3 (`Widget.Material3`).
- Replace deprecated `app:passwordToggleEnabled` with `app:endIconMode="password_toggle"`.

### Compose Integration (Optional but recommended)

#### [MODIFY] [LoginActivity.kt](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/java/com/training/monitor/ui/login/LoginActivity.kt)
- Add a Compose `@Preview` function to provide a "Compose Preview" as requested by the user.
- This preview will use `AndroidView` to wrap the existing XML layout, allowing it to be viewed in the Compose Preview tab.

## Verification Plan

### Manual Verification
- Open `activity_login.xml` and verify the Layout Preview renders without errors.
- Open `LoginActivity.kt` and verify the new Compose Preview renders correctly.
- Ensure the "Security Manager" warning (if it persists) does not block the actual rendering.
