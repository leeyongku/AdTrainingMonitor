# Implementation Plan - Fix Render Issue in Preview

The user is experiencing an inflation error in the preview (`The Security Manager is deprecated... Error inflating the preview`). This is likely caused by XML issues in `activity_login.xml` and a theme mismatch in `AndroidManifest.xml`. Additionally, the user requested a "Compose Preview", which is currently missing from the project.

## User Review Required

> [!IMPORTANT]
> I will be updating `activity_login.xml` to use modern attributes and clean up namespaces. I will also align the application theme in `AndroidManifest.xml` with the one defined in `themes.xml` (Material 3). Finally, I will add a Compose `@Preview` function to `LoginActivity.kt` to allow viewing the layout in the Compose Preview tab.

## Proposed Changes

### [UI Layout]

#### [MODIFY] [activity_login.xml](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/res/layout/activity_login.xml)
- Move `xmlns:app` and `xmlns:tools` to the root `LinearLayout`.
- Add `tools:context=".ui.login.LoginActivity"` to help the renderer.
- Remove redundant local `xmlns:app` definitions from child views.
- Replace `app:passwordToggleEnabled="true"` with `app:endIconMode="password_toggle"` for better Material 3 compatibility.

### [App Configuration]

#### [MODIFY] [AndroidManifest.xml](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/AndroidManifest.xml)
- Update `android:theme` from `@style/Theme.MaterialComponents.Light.NoActionBar` (M2) to `@style/Theme.TrainingMonitor` (M3) to match the project's `themes.xml` definition and avoid theme mismatches during inflation.

### [Compose Preview Implementation]

#### [MODIFY] [LoginActivity.kt](file:///D:/LEE/TEST/AdTrainingMonitor/app/src/main/java/com/training/monitor/ui/login/LoginActivity.kt)
- Add a `@Preview` Composable at the end of the file that uses `AndroidView` to inflate and display `activity_login.xml`. This provides the requested "Compose Preview".

## Verification Plan

### Manual Verification
- Open `activity_login.xml` and verify the Layout Preview (Design tab) renders without errors.
- Open `LoginActivity.kt` and verify the new Compose Preview (Preview tab) renders correctly.
- Ensure that the "Security Manager is deprecated" error no longer blocks inflation.
