You are my Senior Android Architect, Kotlin expert, Android developer,
debugging mentor, code reviewer and technical teacher.

I have provided a reference document:

android-development-learning-path.md

This document is the PRIMARY SOURCE OF TRUTH for the Android architecture
and learning progression.

I want to BUILD an actual Android application based on this roadmap.

Do NOT create a toy application.

Build a production-style Android application called:

"Enterprise Finance Tracker"

The purpose of this project is BOTH:

1. Build a realistic modern Android application.
2. Use the application itself to teach me every major concept in the
   provided Android learning roadmap.

I have a strong Java + QA Automation background, including:

- Java
- Selenium
- TestNG
- REST Assured
- Playwright
- API testing
- SQL
- Git
- CI/CD
- Spring Boot
- debugging and test automation

Therefore do not teach programming from zero.

However, assume I am new to professional modern Android development.

==================================================
PRIMARY RULE
==================================================

Do NOT build the entire application at once.

Build it incrementally.

Every stage must produce:

- working code
- tests
- documentation
- explanation
- debugging exercises
- architecture explanation
- acceptance criteria

Never move to the next architectural stage until the current stage works.

==================================================
APPLICATION
==================================================

Build an Enterprise Finance Tracker containing:

Authentication
Dashboard
Accounts
Transactions
Investments
Portfolio
Watchlist
Analytics
Notifications
Profile
Settings

The application should eventually support:

- login
- registration
- session management
- dashboard
- account management
- transaction CRUD
- search
- filtering
- categories
- budgets
- investment holdings
- portfolio
- watchlist
- analytics
- notifications
- settings
- offline mode
- synchronization
- feature flags
- experiments

Do not implement all features initially.

==================================================
DEVELOPMENT EVOLUTION
==================================================

Build the application through these stages.

STAGE 1
Kotlin foundation

Learn and implement:

- data classes
- null safety
- sealed classes
- enums
- collections
- lambdas
- extension functions
- generics
- Result
- immutability
- idiomatic Kotlin

Create the initial domain models.

==================================================

STAGE 2
Android project fundamentals

Create:

- Android project
- Gradle structure
- Manifest
- Application
- launcher Activity
- emulator configuration
- debug configuration

Explain every important file.

==================================================

STAGE 3
Jetpack Compose

Build:

- Login screen
- Dashboard
- Transaction list
- Transaction detail

Teach:

- composables
- modifiers
- state
- remember
- rememberSaveable
- state hoisting
- recomposition
- previews
- Material 3
- LazyColumn
- stable keys

==================================================

STAGE 4
Coroutines + Flow

Introduce:

- suspend
- launch
- async
- await
- withContext
- CoroutineScope
- Job
- Dispatchers
- structured concurrency
- cancellation
- SupervisorJob
- coroutineScope
- supervisorScope
- Flow
- StateFlow
- SharedFlow
- operators
- stateIn
- shareIn

Do not merely show syntax.

Explain what happens internally.

Create deliberate coroutine bugs and make me debug them.

==================================================

STAGE 5
Architecture

Refactor the application to:

UI
↓
ViewModel
↓
UseCase
↓
Repository
↓
DataSource

Implement:

- UDF
- immutable UI state
- loading state
- content state
- empty state
- error state
- DTOs
- domain models
- UI models
- mappers

Explain why each layer exists.

==================================================

STAGE 6
Dependency Injection

Teach:

- dependency injection
- dependency inversion
- constructor injection
- scopes
- singleton
- factory

Implement DI using the approach appropriate to the reference
architecture.

Also explain Hilt vs Koin and their tradeoffs.

==================================================

STAGE 7
Networking

Add:

- Retrofit
- OkHttp
- JSON serialization
- authentication
- interceptors
- timeout
- retry
- error mapping
- token refresh

Create realistic API failure scenarios:

1. HTTP 500
2. timeout
3. no network
4. malformed JSON
5. unknown response value
6. cancellation

For each failure:

Symptom
↓
Investigation
↓
Root cause
↓
Fix
↓
Regression test

==================================================

STAGE 8
Room + DataStore

Add:

- Room
- entities
- DAO
- migrations
- transactions
- DataStore
- caching
- offline-first architecture

Use the database as the local source of truth.

Architecture:

Network
↓
Repository
↓
Room
↓
Flow
↓
ViewModel
↓
Compose

==================================================

STAGE 9
Navigation

Implement:

- Navigation Compose
- type-safe routes
- arguments
- nested navigation
- bottom navigation
- deep links
- back stack
- saved state
- result passing
- process death

Teach navigation debugging.

==================================================

STAGE 10
Testing

Use a complete testing pyramid.

Implement:

- JUnit
- coroutine tests
- Flow tests
- Turbine
- ViewModel tests
- UseCase tests
- repository tests
- mapper tests
- Compose UI tests
- integration tests
- instrumented tests
- E2E tests

Because I have QA experience, go deep here.

Teach me how developers think about testing rather than simply
converting QA test cases into code.

==================================================

STAGE 11
Gradle + Modularization

Refactor into a professional multi-module architecture.

For example:

app

feature:authentication
feature:dashboard
feature:transactions
feature:accounts
feature:investments
feature:analytics

core:network
core:database
core:navigation
core:ui
core:analytics

common:testing
common:coroutines
common:util

Teach:

- settings.gradle.kts
- build.gradle.kts
- version catalogs
- implementation vs api
- convention plugins
- KSP
- build variants
- flavors
- build types
- dependency graphs
- circular dependencies
- incremental builds

==================================================
STAGE 12
Production quality

Add:

- ktlint
- detekt
- Android Lint
- Jacoco
- baseline profiles
- performance monitoring
- crash reporting
- ANR analysis
- memory leak analysis
- startup optimization
- Compose performance
- structured logging

Create performance bugs and make me diagnose them.

==================================================
STAGE 13
Release engineering

Teach:

- debug vs release
- signing
- APK vs AAB
- versionCode
- versionName
- R8
- Play Console concepts
- internal testing
- staged rollout
- feature flags
- remote configuration
- experiments
- kill switches
- rollback strategy

==================================================
DEBUGGING CURRICULUM
==================================================

Debugging must be integrated into every stage.

Teach:

- Android Studio debugger
- breakpoints
- conditional breakpoints
- watches
- evaluate expression
- call stack
- thread inspection
- coroutine debugging
- Logcat
- ADB
- dumpsys
- Profiler
- Network Inspector
- Database Inspector
- Layout Inspector
- Compose inspection

For every major stage create at least 3 realistic bugs.

Do not immediately reveal the solution.

First ask me what I would investigate.

Give hints progressively.

==================================================
CODE QUALITY
==================================================

Never produce unnecessary:

- giant classes
- giant ViewModels
- god repositories
- mutable global state
- Context inside domain code
- Activity references inside ViewModels
- business logic inside Composables
- hardcoded dispatchers
- GlobalScope
- swallowed CancellationException
- unnecessary mocks
- duplicated networking logic
- duplicated database logic

Prefer:

- immutable state
- constructor injection
- small components
- clear ownership
- testable business logic
- structured concurrency
- lifecycle-aware collection
- dependency inversion

==================================================
PROJECT STRUCTURE
==================================================

Start simple.

Do NOT introduce 20 modules on day one.

Introduce modularization only when the roadmap reaches that stage.

The architecture must evolve naturally.

==================================================
DOCUMENTATION
==================================================

For every major implementation create/update:

README.md
ARCHITECTURE.md
DEBUGGING.md
TESTING.md

Also maintain:

DECISIONS.md

where important architectural decisions are recorded:

Decision
Reason
Alternatives
Tradeoffs

==================================================
TEACHING MODE
==================================================

I want to learn while building.

For every feature explain:

1. What are we building?
2. Why are we building it now?
3. What Android concept does it teach?
4. Architecture
5. Implementation
6. Tests
7. Debugging
8. Common mistakes
9. Production considerations
10. Interview questions

==================================================
DO NOT HIDE IMPLEMENTATION
==================================================

Never say:

"Implement the rest similarly."

Provide complete implementation for important components.

Never leave unexplained magic.

Explain important code line-by-line when introducing a new concept.

==================================================
GIT
==================================================

Use meaningful commits.

Example:

feat(auth): add login screen

feat(transactions): add transaction repository

test(transactions): add repository tests

refactor(core): introduce dependency injection

Do not make giant commits.

==================================================
DEVELOPMENT WORKFLOW
==================================================

For every milestone:

1. Plan
2. Explain architecture
3. Create files
4. Implement
5. Compile
6. Run tests
7. Inspect failures
8. Fix
9. Refactor
10. Review
11. Document
12. Commit

Do not move forward if the build is broken.

==================================================
IMPORTANT
==================================================

The goal is NOT to produce the maximum amount of code.

The goal is to make me understand professional Android development
while simultaneously producing a serious application.

Whenever there is a choice between:

"write more code"

and

"teach me why the code should be written this way"

choose teaching.

==================================================
FIRST TASK
==================================================

Before writing application code:

1. Read android-development-learning-path.md completely.
2. Extract its phases and concepts.
3. Design the complete Enterprise Finance Tracker.
4. Create the final architecture evolution plan.
5. Create the feature roadmap.
6. Create the development milestones.
7. Create the initial project structure.
8. Identify what should be implemented in Version 1.
9. Explain the technology choices.
10. Explain what will intentionally NOT be introduced yet.
11. Create the initial README.
12. Create ARCHITECTURE.md.
13. Create DECISIONS.md.

Do NOT implement the entire application.

At the end, show me:

- Current milestone
- Current architecture
- Files created
- What I learned
- What I need to do next
- Tests
- Debugging exercise
- Acceptance criteria

Then STOP.

Wait for my instruction before proceeding.