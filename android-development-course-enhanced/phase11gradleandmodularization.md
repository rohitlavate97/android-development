# PHASE 11 — GRADLE & MODULARIZATION (Week 16)

**Objective:** Master Android's build system, Kotlin DSL, version catalogs, build variants, R8 optimization, and build caching. Stop being blocked by the build.
**Why this phase matters:** Professional Android apps are built across 50 to 200+ Gradle modules. Without understanding dependency configurations, the Gradle lifecycle, and build caches, incremental builds can degrade to 10+ minutes, dependency conflicts halt CI, and release builds fail due to R8 shrinking.
**Prerequisites:** Phase 1 (Kotlin), Phase 5 (App Architecture), Phase 6 (Dependency Injection).
**Project deliverable:** Expense Tracker v10 — Multi-module architecture refactor with version catalog (`libs.versions.toml`), `feature:*` and `core:*` modules with `-api`/`-impl` split, and product flavors.
**Concepts covered:** 10 total, each with the full 13-step teaching sequence.

*(Tooling Note: While older projects and legacy documentation often cite Gradle 8.x as current, Gradle 9.x is already shipped and active in modern Android development. This guide targets modern Kotlin DSL and Gradle 8+/9+ idioms.)*

---

## Concept 1: The Gradle Build Lifecycle & Execution Model

### 1. What is it
Gradle is a task-based build automation system. It operates on a Directed Acyclic Graph (DAG) of tasks executing in three distinct phases: Initialization, Configuration, and Execution.

### 2. Why does it exist
Unlike Maven, which has a rigid, declarative, linear lifecycle (compile → test → package), Gradle provides a flexible, code-based build execution engine. This allows complex custom build logic (like code generation, conditional compilation, or variant assembly) required by the Android ecosystem.

### 3. Mental model
Think of Gradle like a restaurant kitchen:
- **Initialization:** Looking at the order tickets and deciding which stations (modules) are open.
- **Configuration:** Every chef reading the recipe instructions to figure out *what* they need to do (building the DAG).
- **Execution:** Actually cooking the food (running the tasks) in the correct dependency order.

### 4. How it works
- **Initialization:** Evaluates `settings.gradle.kts` to determine which modules (`projects`) are included in the build.
- **Configuration:** Evaluates every `build.gradle.kts` file in the build. It creates a Task DAG. *Crucially, everything outside of a task action block runs during this phase on every build.*
- **Execution:** Runs the subset of tasks requested (and their dependencies) based on the DAG.

### 5. Code
```kotlin
// build.gradle.kts
println("1. This prints during CONFIGURATION phase. (Runs every time!)")

tasks.register("generateCustomDocs") {
    println("2. This ALSO prints during CONFIGURATION phase when configuring this task.")
    
    // The actual task action
    doLast {
        println("3. This ONLY prints during EXECUTION phase if 'generateCustomDocs' is run.")
        // Write file, make network request, etc.
    }
}
```

### 6. Production usage
Writing custom tasks for CI/CD pipelines, such as automatically generating release notes, pushing APKs to Firebase App Distribution, or running custom detekt/lint validation steps before compilation.

### 7. Common mistakes
❌ **Wrong: Doing heavy work in Configuration**
```kotlin
// In build.gradle.kts
// This network call delays EVERY build, even if you just click "Run"!
val latestVersion = URL("https://api.mycompany.com/version").readText()
```

✅ **Right: Deferring work to Execution**
```kotlin
tasks.register("fetchVersion") {
    doLast {
        val latestVersion = URL("https://api.mycompany.com/version").readText()
        // Save to file or use
    }
}
```

### 8. Debugging
- `gradlew <task> --scan` generates a deep web report.
- `gradlew <task> --profile` generates a local HTML timing report.
- `gradlew tasks --all` lists the DAG.
- If a build is slow before compilation even starts, you have a Configuration Phase performance issue.

### 9. Testing
Gradle provides `ProjectBuilder` for testing custom plugins, but for general build script debugging, create a standalone dummy task using `doLast` to print variables.

### 10. Exercise
Create a custom task `generateBuildInfo` in your `app/build.gradle.kts` that writes the current timestamp and branch name to a `build_info.txt` file in your `build/` directory during execution.

### 11. Deliberate failure
Put `Thread.sleep(5000)` at the top-level of your `app/build.gradle.kts`. Run any unrelated task (like `./gradlew clean`). Observe how the build hangs for 5 seconds doing "Configuring".

### 12. Interview questions
- **Mid:** What is the difference between the Configuration and Execution phases in Gradle?
- **Senior:** Why did Google migrate Android from Groovy DSL to Kotlin DSL, and how does it affect the configuration phase speed? (Hint: Type-safety, IDE autocompletion, but Groovy was historically faster until configuration caching improved).
- **Senior:** How does Gradle's DAG differ from Maven's build lifecycle?

### 13. Checkpoint
Can you explain why doing a file read at the top level of `build.gradle.kts` slows down the entire IDE sync?

---

## Concept 2: Version Catalogs (`gradle/libs.versions.toml`)

### 1. What is it
A centralized TOML file (`gradle/libs.versions.toml`) that declares all dependency versions, libraries, and plugins for the entire multi-module project.

### 2. Why does it exist
In multi-module projects, defining dependencies in every `build.gradle.kts` leads to version mismatches (e.g., `feature-a` uses Retrofit 2.9.0, `feature-b` uses 2.11.0). Version catalogs provide a single source of truth with IDE auto-completion.

### 3. Mental model
It’s like Maven’s `<dependencyManagement>` in a Parent POM, but extracted into a dedicated, readable file format that the IDE parses natively.

### 4. How it works
The `libs.versions.toml` file is parsed by Gradle, generating type-safe accessors under the `libs` object.
- `[versions]` defines string versions.
- `[libraries]` defines Maven coordinates linked to versions.
- `[bundles]` groups multiple libraries together.
- `[plugins]` defines Gradle plugins.

### 5. Code
```toml
# gradle/libs.versions.toml
[versions]
retrofit = "2.11.0"
compose-bom = "2024.02.01"

[libraries]
retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }

# Bom definition
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" } # Version inherited from BOM

[bundles]
networking = ["retrofit-core", "retrofit-gson"]

[plugins]
android-application = { id = "com.android.application", version = "8.3.1" }
```

> **[Extension] Note:** The specific version pins above (`retrofit = "2.11.0"`, `compose-bom = "2024.02.01"`, AGP `"8.3.1"`) are illustrative, not a recommendation to target those exact releases — same caveat as the Gradle 8.x/9.x tooling note at the top of this file. AGP version numbers track Gradle major versions closely (AGP 8.x pairs with Gradle 8.x, AGP 9.x with Gradle 9.x), and the Compose BOM ships new dated releases every few weeks. Whatever numbers appear in a tutorial or in this guide, always check `gradle/libs.versions.toml` in a real, actively maintained project (or the official release notes) for what's actually current before pinning a new project — don't copy version strings out of teaching material verbatim.

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.bundles.networking)
}
```

### 6. Production usage
Reading the `libs.versions.toml` file is the 60-second superpower to understand any unfamiliar Android repo. You instantly see the tech stack (Coroutines vs RxJava, Compose vs Views, Ktor vs Retrofit).

### 7. Common mistakes
❌ **Wrong: Hardcoding versions in modules alongside a catalog**
```kotlin
// Defeats the purpose of the catalog
implementation("com.squareup.retrofit2:retrofit:2.9.0")
```

✅ **Right: Utilizing bundles for common feature dependencies**
```kotlin
implementation(libs.bundles.networking)
```

### 8. Debugging
- If `libs.something` is unresolved, do a Gradle Sync.
- TOML files don't allow uppercase letters in aliases; `composeUI` in TOML becomes `composeui`. Use dashes: `compose-ui` in TOML maps to `libs.compose.ui` in Kotlin.

### 9. Testing
N/A - Catalogs are static configuration.

### 10. Exercise
Migrate your current Expense Tracker dependencies into a `libs.versions.toml` file. Create a bundle for your Room dependencies (`room-runtime`, `room-ktx`).

### 11. Deliberate failure
Add an invalid syntax to the TOML file (e.g., forget a quote). Notice how Gradle completely fails to sync, proving it evaluates this file extremely early.

### 12. Interview questions
- **Mid:** How do you share dependency versions across 50 Gradle modules?
- **Senior:** What is the difference between defining a library in the catalog versus using a BOM (Bill of Materials)?

### 13. Checkpoint
If you have `room-compiler = { ... }` in your TOML, how do you reference it in your module's `dependencies { ... }` block using KSP?

---

## Concept 3: Dependency Configurations (implementation vs api)

### 1. What is it
Configurations dictate how a dependency is exposed to downstream modules that consume the current module, and whether it's included in the final APK.

### 2. Why does it exist
In large projects, if Module A depends on B, and B depends on C, modifying C shouldn't force A to recompile unless A actually *uses* C's types. Proper configuration prevents massive compilation chain reactions.

### 3. Mental model
Compare it to Maven scopes:
- `implementation` ≈ Maven `compile` (but hidden from downstream).
- `api` ≈ Maven `compile` (exposed to downstream).
- `compileOnly` ≈ Maven `provided`.
- `testImplementation` ≈ Maven `test`.

### 4. How it works
- **`implementation`**: The dependency is available internally, but not leaked to consumers of this module. (Fast builds!).
- **`api`**: Leaks the dependency to consumers. If Module A `api`s Retrofit, Module B (which depends on A) can use Retrofit without declaring it. If Retrofit changes, A and B both recompile.
- **`compileOnly`**: Needed for compiling, but not shipped in the APK (e.g., JetBrains annotations, annotation processors).
- **`runtimeOnly`**: Not needed to compile, but must be in the APK (rare in Android, sometimes used for specific logging implementations).

### 5. Code
```kotlin
// core-network/build.gradle.kts
dependencies {
    // Other modules depending on 'core-network' DO NOT get OkHttp.
    // Changing OkHttp version only recompiles core-network.
    implementation(libs.okhttp) 

    // Other modules depending on 'core-network' CAN use Retrofit.
    // Changing Retrofit version forces ALL downstream modules to recompile.
    api(libs.retrofit)
}
```

### 6. Production usage
In modular architecture (like `-api` and `-impl` modules), the `domain` module uses `api` for core data types, but `data` uses `implementation` for Room/SQLDelight so the UI layer can't accidentally access database classes.

### 7. Common mistakes
❌ **Wrong: Using `api` everywhere "just in case"**
This destroys Gradle's incremental compilation, turning a 10-second incremental build into a 3-minute full rebuild.

✅ **Right: Default to `implementation`**
Only upgrade to `api` if the module explicitly exposes the library in its public interface (e.g., returning a `Flow` from a repository means you must `api` Coroutines).

### 8. Debugging
Use `./gradlew app:dependencies` to see the full dependency tree and identify where transitive dependencies are leaking from.

### 9. Testing
N/A - Handled via Gradle compilation validation.

### 10. Exercise
In your project, make a `core-database` module. Add Room via `implementation`. Try to access a Room `@Entity` annotation in your `app` module. It should fail to compile.

### 11. Deliberate failure
Change a widely used library from `implementation` to `api` in a base module. Run a build scan (`--scan`). Revert it, change a file in that base module, and run another scan. Compare the compilation times.

### 12. Interview questions
- **Mid:** What is the difference between `api` and `implementation` in Gradle?
- **Senior:** If Module A implements Module B, and Module B `api`s Gson, what happens to Module A when Gson's version is updated?
- **Senior:** Why is `compileOnly` used for annotation processors?

### 13. Checkpoint
If you have a helper module that wraps logging logic, should the underlying logging framework (like Timber) be `api` or `implementation`?

---

## Concept 4: Android Build Variants, Types & Product Flavors

### 1. What is it
The combination of `buildTypes` (debug/release) and `productFlavors` (free/paid, dev/prod) that generate unique versions of your app from the same codebase.

### 2. Why does it exist
You need an app to hit your staging server while developing, but the production server when released to users. You might also have a "Free" version with ads and a "Paid" version without ads, all sharing 90% of the codebase.

### 3. Mental model
It’s a matrix multiplication.
(Flavors: `free`, `paid`) × (Types: `debug`, `release`) = 
Variants: `freeDebug`, `freeRelease`, `paidDebug`, `paidRelease`.

### 4. How it works
- **Build Types**: Control *how* the app is built (minification, signing, debuggability).
- **Product Flavors**: Control *what* is built (different features, API endpoints, app icons).
- Every flavor must belong to a `flavorDimension` to define grouping.

### 5. Code
```kotlin
// app/build.gradle.kts
android {
    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            buildConfigField("String", "BASE_URL", "\"https://dev.api.expense.com/\"")
            manifestPlaceholders["appName"] = "Expense (Dev)"
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://api.expense.com/\"")
            manifestPlaceholders["appName"] = "Expense"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 6. Production usage
- White-labeling: One codebase generating apps for 10 different banks by changing the `theme` and `applicationId` per flavor.
- Dev/Staging/Prod environments with different server URLs and Analytics API keys via `buildConfigField`.

### 7. Common mistakes
❌ **Wrong: Hardcoding API keys in code**
```kotlin
val apiKey = if (BuildConfig.DEBUG) "dev_key" else "prod_key"
```

✅ **Right: Injecting via flavors**
Injecting via `buildConfigField` in the Gradle configuration so code doesn't need to know about variants.

### 8. Debugging
- Use the "Build Variants" tab in Android Studio (left edge) to switch context. If you are on `devDebug`, IDE won't resolve code in the `src/prod/` source set.
- Check generated `BuildConfig.java` in `build/generated/source/buildConfig/`.

### 9. Testing
You can write specific tests for flavors by putting them in `src/testDev/java/` or `src/androidTestProd/java/`.

### 10. Exercise
Add a `dev` and `prod` flavor to your Expense Tracker. Give the `dev` flavor an `.dev` applicationIdSuffix so you can install both the Dev and Prod versions on your phone simultaneously.

### 11. Deliberate failure
Try to compile code that references a class located in `src/prod/java/` while your active Build Variant in Android Studio is set to `devDebug`. Observe the "Unresolved reference" error.

### 12. Interview questions
- **Mid:** What is the difference between a Build Type and a Product Flavor?
- **Senior:** How do source sets work with flavors? If I have `src/main/res/values/strings.xml` and `src/dev/res/values/strings.xml`, which one wins during a `devDebug` build?

### 13. Checkpoint
What is the fully qualified name of the Build Variant combining the flavor `qa` and the build type `release`?

---

## Concept 5: R8, ProGuard, Shrinking & Obfuscation

### 1. What is it
R8 is Android's default compiler tool that shrinks (removes unused code/resources), obfuscates (renames classes/methods to short letters), and optimizes (inlines code) your release APK. (It replaced legacy ProGuard).

### 2. Why does it exist
An app importing Play Services and Guava might pull in 100,000 methods, but only use 5,000. R8 strips the unused 95,000 methods, reducing the APK size from 50MB to 5MB, and renaming variables deters reverse-engineering.

### 3. Mental model
Think of it like a tree surgeon. They start at the roots (your MainActivity and Manifest), climb every branch (method calls), and chop off any branch they never touch. Obfuscation is like translating the remaining tree tags into a secret code.

### 4. How it works
Enabled via `isMinifyEnabled = true` in `buildTypes`.
If R8 aggressively deletes something it shouldn't (often because it was only accessed via Reflection or JSON serialization), you write `-keep` rules in `proguard-rules.pro`.

### 5. Code
```kotlin
// app/build.gradle.kts
buildTypes {
    release {
        isMinifyEnabled = true     // Code shrinking & obfuscation
        isShrinkResources = true   // Removes unused drawables/layouts
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

```text
# proguard-rules.pro
# Don't obfuscate GSON data models! Otherwise JSON parsing fails.
-keep class com.company.app.data.models.** { *; }

# Keep specific method accessed via reflection
-keepclassmembers class com.company.app.utils.AnalyticsHelper {
    public void trackEvent(...);
}
```

### 6. Production usage
Every production Android app uses R8. Mapping files (`mapping.txt`) are uploaded to Firebase Crashlytics so production stack traces (which look like `a.b.c.d()`) are de-obfuscated back to `ExpenseViewModel.loadData()`.

### 7. Common mistakes
❌ **Wrong: Testing only on Debug**
Testing for 3 weeks on `debug`, then building `release` on Friday afternoon. The app crashes instantly because GSON models were obfuscated and JSON parsing yielded nulls.

✅ **Right: Using `@Keep` or Proguard rules**
Using the `@Keep` annotation on DTOs, or testing a minified build earlier in the cycle.
```kotlin
@Keep // Tells R8: "Do not touch or rename this class"
data class ServerResponse(val id: String)
```

### 8. Debugging
- If a release build crashes but debug works: it's R8.
- Check `build/outputs/mapping/release/usage.txt` to see what R8 deleted.
- Look for `ClassNotFoundException` or `NullPointerException` (due to failed JSON mapping) in release logs.

### 9. Testing
Create a `benchmark` or `staging` build type that is debuggable (`isDebuggable = true`) BUT has `isMinifyEnabled = true`. This allows you to attach a debugger to an obfuscated app.

### 10. Exercise
Enable `isMinifyEnabled = true` in your debug build type temporarily. Add a dummy class, don't use it anywhere, and analyze the generated APK (Build -> Analyze APK) to verify the class was stripped.

### 11. Deliberate failure
Create a Retrofit network response data class. Do NOT add `@Keep`. Enable minification. Run the app, make the network call, and watch it crash or return null fields because `userId` in JSON couldn't map to obfuscated field `a`.

### 12. Interview questions
- **Mid:** What does `isMinifyEnabled` actually do under the hood?
- **Senior:** Why does reflection cause problems with R8/ProGuard, and how do you fix it?
- **Senior:** Explain what `mapping.txt` is and why it's a critical security and debugging artifact.

### 13. Checkpoint
If you see an error `java.lang.NoSuchMethodError: a.b.c.d()`, what went wrong and how do you figure out what `a.b.c.d` actually is?

---
*End of Part 1. Next part covers build caching, multi-module architecture, KSP, and dependency injection in modular projects.*


---

## 6. Build Speed & Caching Strategies

### 1. What is it
A collection of tools and configurations within Gradle (Local/Remote Build Cache, Configuration Cache, Parallel Execution, and Kotlin Symbol Processing) designed to eliminate redundant work and maximize CPU utilization during compilation.

### 2. Why does it exist
In large Java/Kotlin projects, full builds can take tens of minutes. In Android, compiling resources, Kotlin code, running annotation processors, and dexing bytes creates a massive bottleneck. Slow builds break developer flow, drastically reducing productivity.

### 3. Mental model
Think of a large restaurant kitchen. 
- **Parallel Execution:** Having 10 chefs working simultaneously instead of one chef doing everything sequentially.
- **Build Cache:** Using pre-chopped vegetables from the fridge instead of chopping new ones for every order.
- **Configuration Cache:** Memorizing the day's menu and prep steps instead of reading the manual every morning.

### 4. How it works
- **Build Cache (`org.gradle.caching=true`):** Caches task outputs (like compiled `.class` files). If the task's inputs (source files, dependencies) haven't changed, Gradle skips the task and pulls the output from the cache. Can be local (your machine) or remote (shared via CI).
- **Configuration Cache (`org.gradle.configuration-cache=true`):** Caches the task graph. Gradle normally spends time figuring out *what* to run before actually running it. This skips that step.
- **Parallel Execution (`org.gradle.parallel=true`):** Allows Gradle to build independent modules (e.g., `:feature:login` and `:feature:dashboard`) on separate CPU threads simultaneously.
- **KSP (Kotlin Symbol Processing):** Replaces the legacy `kapt` (Kotlin Annotation Processing Tool). `kapt` had to generate Java stubs to run Java annotation processors on Kotlin code (taking massive time). KSP analyzes Kotlin AST directly, running up to 2x faster.

> **[Extension] KSP2 and Isolated Projects:** Two follow-on developments worth knowing about, both extending ideas already in this section:
> - **KSP2** is a from-scratch reimplementation of KSP built on Kotlin's K2 compiler frontend, shipped as the default starting with `com.google.devtools.ksp` 2.0.0+. It runs as an out-of-process compiler phase rather than a Gradle-plugin-embedded pass, which fixes several long-standing class-loading and incremental-processing bugs KSP1 had. You opt in by pinning a 2.0+ KSP version in your version catalog — no code changes needed for most annotation processors.
> - **Isolated Projects** is a newer, stricter evolution of the Configuration Cache idea: instead of caching one shared task graph, it forbids a module's `build.gradle.kts` from directly reaching into another module's project model at configuration time, so each module can be configured (and cached) fully independently and in parallel. It is still an incubating, opt-in feature (`org.gradle.unsafe.isolated-projects`) as of this writing, not yet the default — but it's the direction Gradle's performance work is heading, and it's the same theme as the Gradle 8.x/9.x note earlier in this file: check what's actually current and stable before you build a CI strategy around an incubating flag.

### 5. Code
**`gradle.properties` (Enable performance flags):**
```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.daemon=true
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC
```

**Migrating from kapt to KSP in `build.gradle.kts`:**
```diff
 plugins {
-    id("kotlin-kapt")
+    id("com.google.devtools.ksp")
 }

 dependencies {
-    kapt("androidx.room:room-compiler:2.6.1")
+    ksp("androidx.room:room-compiler:2.6.1")
 }
```

### 6. Production usage
Enterprise teams strictly monitor build times using **Gradle Enterprise** (Build Scans). Remote build caches are populated by the CI server on the `main` branch, meaning developers pulling the latest `main` download compiled artifacts rather than building locally.

### 7. Common mistakes
- **Dynamic BuildConfig values:** Injecting `System.currentTimeMillis()` into a `BuildConfig` field on every build. This changes the inputs for the compilation task, invalidating the cache *every single time*.
- **Leaving `kapt` applied:** If even one module uses `kapt`, the Java stub generation penalty is paid for that module.

### 8. Debugging
Run your build with a **Build Scan**:
```bash
./gradlew assembleDebug --scan
```
This generates a web report showing exactly where time was spent, which tasks missed the cache, and why (e.g., "Input property 'source' changed").

### 9. Testing
To test if your cache works:
1. Run `./gradlew assembleDebug`.
2. Run it again: `./gradlew assembleDebug`.
3. The second run should take seconds and report `X actionable tasks: X up-to-date`.

### 10. Exercise
In your project, check your `gradle.properties`. Ensure caching, parallel execution, and configuration cache are enabled. Replace any instance of `kapt` with `ksp`.

### 11. Deliberate failure
Add this to a `build.gradle.kts`:
```kotlin
android {
    defaultConfig {
        buildConfigField("String", "BUILD_TIME", "\"${System.currentTimeMillis()}\"")
    }
}
```
Run `assembleDebug` twice. Observe that the Kotlin compilation task NEVER hits the cache. Remove it.

### 12. Interview questions
- **Q:** What is the difference between the Gradle Build Cache and the Configuration Cache?
  - *A:* Build Cache stores the *outputs* of tasks (like compiled code). Configuration Cache stores the *execution plan* (the task graph) so Gradle doesn't have to re-evaluate build scripts on subsequent runs.
- **Q:** Why did the community move from `kapt` to `KSP`?
  - *A:* `kapt` requires generating Java stubs to run standard Java annotation processors, which is extremely slow. KSP understands Kotlin natively and doesn't require stub generation.

### 13. Checkpoint
If you modify a layout XML file in `:feature:login`, does it invalidate the Kotlin compile cache for `:feature:dashboard`?
*(Answer: No, because they are separate modules and resources don't affect downstream Kotlin compilation unless the R class API changes).*

---

## 7. Convention Plugins & `build-logic`

### 1. What is it
Convention plugins are custom Gradle plugins, usually written in Kotlin, that encapsulate shared build configuration. They live in a special included build (often named `build-logic`) and are applied to application and library modules.

### 2. Why does it exist
As an app scales to 50+ modules, copy-pasting the `android { compileSdk = 34 ... }` block, Compose configuration, and common dependencies across 50 `build.gradle.kts` files becomes a maintenance nightmare. Upgrading a version requires changing 50 files.

### 3. Mental model
Think of convention plugins as **base classes** or **traits** for your build scripts. Instead of duplicating the same setup, modules just say: "I am an Android Feature module, configure me."

### 4. How it works
You create a separate Gradle project (e.g., `build-logic/convention`) that compiles custom plugins. These plugins use the Gradle API to configure the Android block, add dependencies, etc. The main project includes this build, allowing modules to apply your custom plugins by ID.

### 5. Code
**1. `build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt`**
```kotlin
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            // Configure common Android block
            extensions.configure<LibraryExtension> {
                compileSdk = 34
                defaultConfig {
                    minSdk = 26
                }
            }

            // Add common dependencies
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", libs.findLibrary("androidx.core.ktx").get())
                add("testImplementation", libs.findLibrary("junit").get())
            }
        }
    }
}
```

**2. A consumer module `feature/transactions/build.gradle.kts`**
```kotlin
// Look how clean this is!
plugins {
    id("expensetracker.android.feature")
    id("com.google.devtools.ksp") // Module-specific plugins
}

dependencies {
    implementation(project(":core:database")) // Module-specific dependencies
}
```

### 6. Production usage
This is the modern standard for Android multi-module apps. The official [Now in Android (NiA)](https://github.com/android/nowinandroid/tree/main/build-logic) architecture uses `build-logic` extensively to manage its 50+ modules.

### 7. Common mistakes
- **Legacy `subprojects {}` blocks:** In older Java/Android projects, people put `subprojects { android { ... } }` in the root `build.gradle`. This is heavily discouraged now because it breaks Gradle's Configuration Cache and project isolation.
- **Making plugins too specific:** Baking in dependencies that only half the modules need.

### 8. Debugging
If a plugin isn't applying correctly, use `println("Applying feature plugin to ${target.name}")` inside the plugin's `apply` method, or attach a debugger via `./gradlew --no-daemon -Dorg.gradle.debug=true`.

### 9. Testing
You can write unit tests for your custom plugins using Gradle TestKit to verify they apply the correct configuration.

### 10. Exercise
Create a `build-logic` folder, set up a minimal `AndroidLibraryConventionPlugin`, and apply it to two of your core modules to remove duplicate `android { ... }` blocks.

### 11. Deliberate failure
Try putting `subprojects { android { compileSdk = 34 } }` in the root `build.gradle.kts`. You will get an error that `android` is not found, because the root project is not an Android module.

### 12. Interview questions
- **Q:** Why use `build-logic` convention plugins instead of a root `subprojects` block?
  - *A:* Convention plugins allow for explicit application, keep the build scripts declarative, and most importantly, support Gradle's strict Configuration Cache and project isolation, leading to faster builds.

### 13. Checkpoint
If you need to update the `compileSdk` from 34 to 35 across 40 modules, how many files should you have to touch in a well-configured project?
*(Answer: Exactly one file — the convention plugin or the version catalog it reads from).*

---

## 8. Multi-Module Architecture Topology

### 1. What is it
The strategic organization of Gradle modules in a project. Rather than organizing strictly by technical layers (UI, Domain, Data), modern Android apps slice vertically by **Feature**, supported by horizontal **Core** modules.

### 2. Why does it exist
If you modularize by layer (e.g., `:data`, `:ui`, `:domain`), changing the `TransactionDao` inside `:data` invalidates `:domain` and `:ui`, causing the *entire app* to recompile. Layer-based modularization bottlenecks build speeds.

### 3. Mental model
- **Layer slicing (Bad):** Cutting a cake horizontally. If the bottom layer is bad, you ruin the whole cake.
- **Feature slicing (Good):** Cutting a cake into wedges. If one wedge is changed, the other wedges are completely unaffected.

### 4. How it works
Ideal Topology:
- `:app`: The root application module. Wires everything together (DI, Navigation).
- `:feature:*`: Vertically sliced features (e.g., `:feature:dashboard`, `:feature:settings`). They do not depend on each other.
- `:core:*`: Shared capabilities (e.g., `:core:network`, `:core:designsystem`, `:core:database`). Features depend on these.

### 5. Code
**`settings.gradle.kts`**
```kotlin
include(":app")
include(":core:designsystem")
include(":core:network")
include(":feature:dashboard")
include(":feature:transactions")
```

**`:feature:dashboard/build.gradle.kts`**
```kotlin
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    // Notice: NO dependency on :feature:transactions
}
```

### 6. Production usage
This topology is used by almost every major tech company (Slack, Uber, Tinder). It enables hundreds of engineers to work simultaneously without constantly recompiling each other's code.

### 7. Common mistakes
- **The "Common" or "Core" garbage dump:** Creating a single `:core` module and putting *everything* in it (networking, UI widgets, utils). It becomes a monolith, and changing a string in it recompiles the whole app.
- **Feature-to-Feature dependencies:** Making `:feature:dashboard` depend on `:feature:settings`. This creates tight coupling and ruins parallel compilation.

### 8. Debugging
Use `./gradlew :app:dependencies` to see the dependency tree. If `:feature:a` pulls in `:feature:b`, you have a topology violation.

### 9. Testing
Because features are isolated, you can run `./gradlew :feature:dashboard:test` and it will only compile the dashboard and its core dependencies, taking seconds instead of minutes.

### 10. Exercise
Map out the module topology of your Expense Tracker on a piece of paper. Ensure no feature depends on another feature.

### 11. Deliberate failure
Add `implementation(project(":feature:dashboard"))` to `:feature:transactions`, and add `implementation(project(":feature:transactions"))` to `:feature:dashboard`. Sync Gradle. You will receive a **Circular Dependency** error.

### 12. Interview questions
- **Q:** Why shouldn't feature modules depend on other feature modules?
  - *A:* It breaks parallel execution, creates tight coupling, increases incremental build times, and risks circular dependencies.

### 13. Checkpoint
If `:feature:cart` needs to navigate to `:feature:checkout`, how do they do it without depending on each other?
*(Answer: They use deep links, or rely on a shared `:core:navigation` module, or the `:app` module handles the routing).*

---

## 9. The `-api` / `-impl` Module Split & Dependency Inversion

### 1. What is it
Splitting a single feature into two separate Gradle modules:
1. `api` module: Contains only interfaces, data classes, and contracts. (Lightweight, compiles instantly).
2. `impl` module: Contains ViewModels, Repositories, DAOs, and UI. (Heavy, changes often).

### 2. Why does it exist
Sometimes Feature A *really* needs to talk to Feature B. But if Feature A depends on Feature B directly, any internal code change in Feature B recompiles Feature A. By depending only on Feature B's `api` module, Feature A ignores internal changes.

### 3. Mental model
The `-api` module is a restaurant's **Menu**. The `-impl` module is the **Kitchen**. 
As a customer (another module), you only need to look at the Menu (`api`). You don't care if they swap the brand of oven in the Kitchen (`impl`); your order process remains exactly the same.

### 4. How it works
- `:feature:auth-api` contains `interface AuthService { fun logout() }`.
- `:feature:auth-impl` contains `class RealAuthService : AuthService { ... }` and depends on `-api`.
- `:feature:settings` depends ONLY on `:feature:auth-api`.
- `:app` depends on BOTH, and uses Dependency Injection (Hilt/Koin) to bind `RealAuthService` to `AuthService`.

### 5. Code
**`:feature:transactions-api` (Contract)**
```kotlin
interface TransactionRepository {
    suspend fun getTransactions(): List<Transaction>
}
```

**`:feature:transactions-impl` (Implementation)**
```kotlin
internal class RealTransactionRepository(
    private val dao: TransactionDao
) : TransactionRepository {
    override suspend fun getTransactions() = dao.getAll()
}

// DI Module inside the impl module
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransactionModule {
    @Binds
    abstract fun bindRepo(impl: RealTransactionRepository): TransactionRepository
}
```

### 6. Production usage
Popularized by companies like Tinder, this is the ultimate solution for large codebases to guarantee fast incremental builds.

### 7. Common mistakes
Putting implementation details (like Retrofit interfaces or Room DAOs) into the `-api` module. The `-api` module should be almost entirely pure Kotlin.

### 8. Debugging
If an `-api` module takes a long time to compile, check its dependencies. It should have almost zero dependencies.

### 9. Testing
Because consuming modules only depend on the `-api` interface, creating fakes/mocks for unit tests in downstream modules is incredibly easy.

### 10. Exercise
Take a feature in your app, extract its public interfaces into an `-api` module, move everything else to `-impl`, and bind them in the `:app` module.

### 11. Deliberate failure
Change a function signature in the `-api` module. Notice that all consuming modules recompile (and fail). Change a line inside the `ViewModel` in the `-impl` module. Notice that consuming modules do *not* recompile.

### 12. Interview questions
- **Q:** How does the API/Implementation module split solve the build time problem of inter-feature dependencies?
  - *A:* It uses Dependency Inversion. Consumers depend on the stable `-api` interface. Changes to the `-impl` module (which happen frequently) do not trigger recompilation of the consumers, saving massive amounts of build time.

### 13. Checkpoint
Can `:feature:dashboard-impl` depend on `:feature:auth-impl`?
*(Answer: NO. `-impl` modules should never depend on other `-impl` modules. They should only depend on `-api` modules).*

---

## 10. Encapsulation & Visibility in Multi-Module Codebases

### 1. What is it
Using Kotlin's `internal` visibility modifier to restrict classes, functions, and properties so they are only visible within the Gradle module they are compiled in.

### 2. Why does it exist
In Java (without Java 9 modules), if a class needed to be accessed by another package, it often had to be `public`. In Android multi-module apps, if you make everything `public`, other modules will bypass your interfaces and directly couple to your implementations.

### 3. Mental model
- `private`: Only visible in this room (file/class).
- `internal`: Only visible in this building (module).
- `public`: Visible to the whole world.

### 4. How it works
By marking implementation details (`RealRepository`, `ViewModel`, `Fragment`, Room `Dao`) as `internal`, the compiler strictly forbids any other Gradle module from importing them. Only the `-api` interfaces remain `public`.

### 5. Code
```kotlin
// module: :feature:transactions

// PUBLIC contract - accessible to other modules
interface AddTransactionUseCase {
    suspend operator fun invoke(amount: Double)
}

// INTERNAL implementation - hidden from other modules
internal class DefaultAddTransactionUseCase @Inject constructor(
    private val repo: TransactionRepository
) : AddTransactionUseCase {
    override suspend operator fun invoke(amount: Double) { ... }
}

// INTERNAL View - Only navigated to via deep links or DI
@Composable
internal fun TransactionScreen(viewModel: TransactionViewModel) { ... }
```

### 6. Production usage
Critical in SDK development. When you ship an SDK, you only want developers to see the public facade, not the messy internal engine. The same logic applies to internal feature modules.

### 7. Common mistakes
Defaulting to `public` (which is Kotlin's default) for everything. This creates a "leaky abstraction" where module A uses module B's classes that it was never supposed to know about.

### 8. Debugging
If you get an "Unresolved reference" error when trying to use a class from an imported module, check if that class is marked `internal`.

### 9. Testing
Unit tests inside the *same module* can access `internal` classes just fine. This means you can thoroughly test `DefaultAddTransactionUseCase` without exposing it to the rest of the app.

### 10. Exercise
Go through your `:core:network` module. Mark all Retrofit interfaces, DTOs, and Interceptors as `internal`. Only expose a clean `public` Repository interface.

### 11. Deliberate failure
Make a class `internal` in `:feature:login`. Try to instantiate it in the `:app` module directly (without DI). Observe the strict compile-time error: `Cannot access 'X': it is internal in 'login'`.

### 12. Interview questions
- **Q:** How does Kotlin's `internal` keyword relate to Gradle modules?
  - *A:* `internal` restricts visibility to the compilation unit. In Android development, a Gradle module maps directly to a Kotlin compilation unit.

### 13. Checkpoint
If a DI module (e.g., Hilt `@Module`) is inside an `-impl` module, should it be `public` or `internal`?
*(Answer: `internal`. The DI framework generates code that bridges the internal bindings, so the module itself doesn't need to be public).*

---

## Phase 11 Project — Expense Tracker v10 (Multi-Module Refactor)

**Goal:** Refactor the monolithic Expense Tracker into a multi-module architecture.

**Requirements:**
1. **Module Topology:**
   - `:app` (Root orchestrator & DI graph assembler)
   - `:core:model` (Domain entities, zero dependencies)
   - `:core:database` (Room database, entities, DAOs)
   - `:core:network` (Retrofit, OkHttp, API clients)
   - `:core:ui` (Design tokens, reusable Compose widgets)
   - `:feature:transactions-api` (Contract: `TransactionListContract`, public routes)
   - `:feature:transactions` (Implementation: ViewModels, screens, internal repo)
2. **Build Configuration:**
   - Single root `gradle/libs.versions.toml` managing all dependencies.
   - Setup product flavors: `whitelabel` dimension with `personal` and `enterprise` flavors.
   - Enable configuration cache and build cache in `gradle.properties`.
3. **Verification:**
   - Run `./gradlew assembleEnterpriseDebug --scan` and observe module parallel execution.
   - Modify a private function in `:feature:transactions` and verify with `--dry-run` or Build Scan that `:feature:transactions-api` and peer modules do NOT recompile.

---

## Phase 11 Checkpoint

Answer without looking:
1. What recompiles across a 50-module project when you change a function body inside an `impl` module vs inside an `api` module?
2. What is the difference between `implementation` and `api` dependency declarations, and why does overusing `api` destroy build performance?
3. How does the `-api` / `-impl` module pattern prevent circular dependencies between two features (e.g. Dashboard needing Transactions, and Transactions needing Dashboard)?
4. Why does adding business logic or expensive operations inside the top-level body of `build.gradle.kts` slow down even simple tasks like `./gradlew help`?
5. What happens when R8 runs in a release build if a data class parsed by Moshi/Gson is NOT annotated or protected by a `-keep` rule?

---

## Complete Maven (`pom.xml`) → Gradle Kotlin DSL & Modularization Translation Table

| Maven (`pom.xml`) Concept | Gradle (`build.gradle.kts`) Equivalent | Notes |
|---|---|---|
| `<dependencies><dependency>...` | `dependencies { implementation(...) }` | Dependency block |
| `<scope>compile</scope>` | `api(...)` | Transitive compile & runtime dependency |
| `<scope>runtime</scope>` | `runtimeOnly(...)` | Included in APK, not on compile classpath |
| `<scope>provided</scope>` | `compileOnly(...)` | Compile-time only (e.g. annotations) |
| `<scope>test</scope>` | `testImplementation(...)` / `androidTestImplementation` | Unit tests vs Emulator tests |
| Multi-module Parent POM `<modules>` | `settings.gradle.kts` (`include(":app", ...)`) | Project module tree |
| `<dependencyManagement>` | Version Catalog (`gradle/libs.versions.toml`) | Centralized version & dependency definitions |
| Maven Profiles (`<profiles>`) | `buildTypes` & `productFlavors` | Build variant matrix |
| Maven Clean / Package Plugins | Gradle Tasks (`./gradlew assembleRelease`) | Task graph execution |
| ProGuard / Shade Plugin | R8 Shrinker (`isMinifyEnabled = true`) | Optimization & obfuscation |
