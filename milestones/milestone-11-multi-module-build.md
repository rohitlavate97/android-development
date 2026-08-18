# Milestone 11: Multi-Module Build

## Title, Goal & Phase Alignment
**Goal:** Modularize the monolithic app structure to improve build times, enforce strict architectural boundaries, and leverage build caching.
**Phase:** Expense Tracker v10 - Scalability

## Architecture & Component Blueprint
- **:app module:** Thin entry point, ties dependencies together.
- **:core:* modules:** Cross-cutting concerns (`:core:network`, `:core:database`, `:core:designsystem`).
- **:feature:* modules:** Isolated domain features (`:feature:home`, `:feature:settings`).
- **API/Implementation Split:** `:feature:home-api` vs `:feature:home-impl`.
- **Version Catalog:** Centralized dependency management via `libs.versions.toml`.

## Step-by-Step Implementation Instructions
1. Extract hardcoded dependencies into `gradle/libs.versions.toml`.
2. Extract common UI components into `:core:designsystem`.
3. Create `:feature:transactions` module and migrate code.
4. Setup Dagger/Hilt or Koin to inject dependencies across module boundaries.
5. Enable Gradle Build Cache in `gradle.properties`.
6. Define build variants (product flavors) like `dev` and `prod`.

## Code Snippets & Signatures
```toml
# libs.versions.toml
[versions]
kotlin = "1.9.22"
compose = "1.6.0"

[libraries]
compose-ui = { group = "androidx.compose.ui", name = "ui", version.ref = "compose" }

[plugins]
android-application = { id = "com.android.application", version = "8.2.0" }
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.feature.transactions)
    implementation(libs.compose.ui)
}
```

## Deliberate Bugs to Catch & Debug
- Circular module dependencies (e.g., `:core:network` depending on `:feature:auth` which depends on `:core:network`).
- Exposing implementation details via `api` instead of `implementation` in Gradle dependencies.
- Version mismatch across modules causing runtime crashes.

## Unit Testing Requirements (Given-When-Then)
- **Given** a change in a single `:feature` module, **When** Gradle builds, **Then** only that module and `:app` are recompiled.
- **Given** `libs.versions.toml`, **When** a version is updated, **Then** all modules inherit the update safely.

## Acceptance Criteria Checklist
- [ ] Version catalog (`libs.versions.toml`) is the single source of truth for dependencies.
- [ ] Architecture split into cohesive `:core` and `:feature` modules.
- [ ] Gradle build cache enabled and functioning.
- [ ] Product flavors (`dev`, `prod`) created with separate backend URLs.
