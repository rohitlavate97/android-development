# Architecture Blueprint — Enterprise Finance Tracker

## 1. High-Level Target Architecture

The application implements Google's recommended **Clean Architecture with Unidirectional Data Flow (UDF)**:

```
┌────────────────────────────────────────────────────────┐
│                        UI Layer                        │
│   Jetpack Compose (Stateless Screens + Atomic Atoms)   │
│                          ↓                             │
│   ViewModel (MVI State Machine / MVVM State Holder)    │
│              (StateFlow<UiState> + UI Intents)         │
└──────────────────────────┬─────────────────────────────┘
                           │ (Inward Dependency: calls UseCases)
┌──────────────────────────▼─────────────────────────────┐
│                      Domain Layer                      │
│        Use Cases (operator fun invoke())               │
│   Domain Entities & Value Classes (Pure Kotlin)        │
│          Repository Interfaces (Contracts)             │
└──────────────────────────▲─────────────────────────────┘
                           │ (Dependency Inversion: Implemented by Data)
┌──────────────────────────┴─────────────────────────────┐
│                       Data Layer                       │
│        Repository Implementations (SSOT)               │
│      Local DataSource       │      Remote DataSource   │
│   (Room SQLite + DataStore) │    (Retrofit + OkHttp)   │
└────────────────────────────────────────────────────────┘
```

---

## 2. Why Each Layer Exists

| Layer | Responsibility | Why It Exists | What It Must NOT Do |
|---|---|---|---|
| **UI (Compose)** | Renders UI based on immutable state. Emits user interaction events (Intents). | Pure presentation. Decoupled from lifecycle and business logic. | Never perform business math, format currencies manually, or query repositories directly. |
| **ViewModel** | State Holder & Reducer. Converts Domain streams to UI State models. | Survives configuration changes and acts as a bridge between UI and Domain. | Never reference `Context`, `View`, or `Activity`. Never contain SQLite/HTTP code. |
| **UseCase (Domain)** | Single executable business action (`operator fun invoke()`). | Reusable, self-contained business logic testable in pure JVM without mocking frameworks. | Never import `android.*` SDK packages. Never depend on Data implementations. |
| **Repository (Data)** | Single Source of Truth (SSOT). Coordinates local and remote data sources. | Hides where data comes from (database vs network vs cache) from the domain layer. | Never expose raw DTOs or database entities to the Domain. |
| **DataSource (Data)** | Low-level data I/O (Room SQLite queries, Retrofit HTTP requests). | Direct database/network driver interaction. | Never implement domain business logic or cross-entity validations. |

---

## 3. Data Model Taxonomy & Boundary Discipline

To prevent architectural leaks, models are strictly separated:

1. **Domain Models (`DomainModels.kt`)**: Pure Kotlin representations containing core business logic and invariant validation. Uses `@JvmInline value class` for identifiers.
2. **Data Transfer Objects (`*Dto.kt`)**: Match remote backend JSON schemas. All fields nullable to defensively withstand malformed server payloads.
3. **Database Entities (`*Entity.kt`)**: Room SQLite table definitions optimized for relational storage and foreign keys.
4. **UI State Models (`*UiState.kt` / `*UiModel.kt`)**: Immutable sealed hierarchies and pre-formatted strings representing the 4 standard visual states (`Loading`, `Content`, `Empty`, `Error`).
