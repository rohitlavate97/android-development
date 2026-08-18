# Milestone 6: Dependency Injection

## Title, Goal & Phase Alignment
**Goal**: Wire application dependencies using automated dependency injection to decouple instantiation from usage and simplify testing.
**Phase**: Phase 6 (Expense Tracker v5)

## Architecture & Component Blueprint
- **DI Graph**: Centralized configuration of dependencies.
- **Hilt**: Using `@Module`, `@Binds`, and `@Provides` for compile-time safety.
- **Koin**: Using `singleOf`, `viewModelOf`, and DSLs for runtime flexibility.
- **Scope Lifetimes**: Properly scoping dependencies (Singleton vs. ViewModel/Activity scopes).
- **Test Doubles**: Injecting `FakeExpenseRepository` for hermetic testing.

## Step-by-Step Implementation Instructions
1. Implement a Hilt module to bind your `ExpenseRepositoryImpl` to `ExpenseRepository`.
2. Annotate the `Application` class with `@HiltAndroidApp` and ViewModels with `@HiltViewModel`.
3. In a separate package/flavor, implement the Koin equivalent using `module { }`.
4. Define scopes: Keep UseCases unscoped, Repositories as Singleton.
5. In tests, replace the real DI graph with a test module providing `FakeExpenseRepository`.

## Code Snippets & Signatures
```kotlin
// Hilt Implementation
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        impl: ExpenseRepositoryImpl
    ): ExpenseRepository
}

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val getTransactionsUseCase: GetTransactionsUseCase
) : ViewModel() { ... }


// Koin Implementation
val appModule = module {
    single<ExpenseRepository> { ExpenseRepositoryImpl(get()) }
    factoryOf(::GetTransactionsUseCase)
    viewModelOf(::TransactionViewModel)
}
```

## Deliberate Bugs to Catch & Debug
- **Bug**: Annotating ViewModels with `@Singleton`, leading to state leaks across completely different screens.
- **Bug**: Missing `@InstallIn` in Hilt modules causing compile errors.
- **Bug**: Forgetting to initialize Koin in the Application class `onCreate`.

## Unit Testing Requirements (Given-When-Then)
- **Given** a Fake repository bound in test DI **When** the ViewModel is requested **Then** it receives the Fake instance.
- **Given** unscoped UseCases **When** injected twice **Then** two different instances are provided.

## Acceptance Criteria Checklist
- [ ] Hilt setup compiles and provides ViewModels.
- [ ] Koin module correctly defines graph.
- [ ] Repositories are singletons, ViewModels are scoped to the UI lifecycle.
- [ ] Fakes are successfully injected during integration tests.
