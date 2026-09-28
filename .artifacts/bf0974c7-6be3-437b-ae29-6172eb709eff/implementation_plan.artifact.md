# Fix Unresolved Reference for Kotlin Plugin in Version Catalog

The project is failing to build because `libs.plugins.kotlin.android` is used in `app/build.gradle.kts` but is not defined in `gradle/libs.versions.toml`. This causes the Gradle KTS compiler to misinterpret the `kotlin` part of the accessor as an extension function, leading to a "receiver type mismatch" error.

## Proposed Changes

### [gradle/libs.versions.toml](file:///D:/LEE/TEST/AdTrainingMonitor/gradle/libs.versions.toml)

- Add a `kotlin` version to the `[versions]` block.
- Add the `kotlin-android` plugin definition to the `[plugins]` block.

### [build.gradle.kts (root)](file:///D:/LEE/TEST/AdTrainingMonitor/build.gradle.kts)

- Add the Kotlin plugin to the root `plugins` block with `apply false` to manage its version centrally.

## Verification Plan

### Automated Tests
- Run `./gradlew help` or a build task to verify that the scripts compile and the plugin is resolved.
