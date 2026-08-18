# Android Development — Zero to Best-in-Class

A self-contained, 18-week curriculum for taking a QA/test-automation engineer (Selenium, Appium, TestNG, backend/API testing background) to a production-grade Android developer. Built as a set of prompts and reference documents for an AI instructor, but fully usable as a standalone self-study course.

> **[Extension]** This README did not exist in a usable form in the original upload (the file on disk was a UTF-16LE-encoded, one-line stub reading only `# android-development`). It has been rebuilt here as a proper index. No course content was changed to produce it.

## How the course is organized

There are three layers of documents. Read them in this order:

1. **`masterpromptandroidlearningguide.md`** — the system/kickoff prompt. This defines the AI instructor's role, the learner's starting background, the Definition of Done, the mandatory 13-step teaching sequence used for every concept, the running "Enterprise Expense & Investment Tracker" project, the debugging curriculum, pacing, and delivery rules. **Start here** if you are pasting this course into an AI assistant to have it teach you interactively. This file also defines the rule that governs everything below: known-stale facts in the reference doc are to be *flagged when they come up*, not silently rewritten, and any content not present in the original source material is to be labeled `[Extension]`.
2. **`androiddevelopmentlearningpath.md`** — the reference curriculum / source of truth. A vendor-neutral map of modern Android development: the landscape, the "options map" of competing tools and patterns (Hilt vs Koin, MVVM vs MVI, Retrofit vs Ktor, etc.), condensed Phase 1–13 outlines, a suggested schedule, a universal anti-patterns table, a glossary, and a guide to reading an unfamiliar codebase in 90 minutes. Its **Appendix** applies all of this to a real, specific codebase (`Mistplay/mistplay-android`) — module topology, the custom MVI engine, house rules, CI/CD, and a 7-step local practice track. The appendix is intentionally kept separate from the general body: general Android knowledge vs. "how this one repo happens to do it" are never blended.
3. **`phase1kotlin.md` through `phase13releaseengineeringandproductionplaybook.md`** — the full 13-phase course, each phase expanding the corresponding condensed outline from `androiddevelopmentlearningpath.md` into full teaching content using the mandatory 13-step sequence per concept (what it is → why it exists → mental model → how it works → code → production usage → common mistakes → debugging → testing → exercise → deliberate failure → interview questions → checkpoint).

## Phase map

| Phase | File | Weeks | Focus |
|---|---|---|---|
| 1 | `phase1kotlin.md` | 1–2 | Kotlin language fundamentals |
| 2 | `phase2coroutinesandflow.md` | 3–4 | Coroutines & Flow |
| 3 | `phase3androidplatformfundamentals.md` | 5 | Android platform fundamentals (lifecycle, manifest, storage, ADB) |
| 4 | `phase4jetpackcompose.md` | 6–8 | Jetpack Compose |
| 5 | `phase5apparchitecture.md` | 9–10 | App architecture (Clean Architecture, MVVM, MVI) |
| 6 | `phase6dependencyinjection.md` | 9–10 | Dependency injection (Hilt & Koin) |
| 7 | `phase7networking.md` | 11 | Networking (Retrofit, Ktor, OkHttp) |
| 8 | `phase8localpersistence.md` | 12 | Local persistence (Room, DataStore) |
| 9 | `phase9navigation.md` | 13 | Navigation-Compose |
| 10 | `phase10testing.md` | 14–15 | Testing (unit, Flow, Compose UI, screenshot, E2E) |
| 11 | `phase11gradleandmodularization.md` | 16 | Gradle & modularization |
| 12 | `phase12qualityperformanceandobservability.md` | 17 | Quality, performance & observability |
| 13 | `phase13releaseengineeringandproductionplaybook.md` | 18 | Release engineering, security & production playbook |

Every phase file ends with a project milestone, a checkpoint quiz, and a translation table mapping your existing QA/automation/backend vocabulary onto the Android equivalent.

## Known stale facts, flagged (not silently fixed)

The reference doc's appendix (`androiddevelopmentlearningpath.md`, Appendix Part A, "The Stack") describes a specific real codebase's dependency versions at a point in time. Per the master prompt's explicit instruction, these are **flagged where they become relevant during teaching, not quietly corrected**, since silently rewriting someone else's factual claims about their own repo is worse than surfacing the discrepancy:

- **Kotlin 2.3.21** — trails the current stable Kotlin line by one minor version as of the document's stated timeframe.
- **Glide 5 ("legacy")** — does not correspond to any real shipped Glide release; Glide's latest stable line is 4.x. Likely a documentation error rather than a fact to teach.
- **Coil 2.7** ("modern default") — Coil 3.x has been the actively developed line since late 2024; the doc's framing as "modern default" is now dated.
- **Gradle 8.14.3** / AGP 9.2.1 — cited as current, but Gradle 9.x had already shipped before the document's stated date.

If you're studying this course with an AI instructor, it should raise each of these the first time it becomes relevant (Kotlin version discussions, image loading, Gradle/build tooling) rather than editing history. `phase11gradleandmodularization.md` already models this pattern with its own Gradle 8.x/9.x tooling note — use that phrasing as the template if you spot other stale facts while studying.

## Using this course

- If you have access to an AI assistant, paste `masterpromptandroidlearningguide.md` in as the system/first message, then follow its delivery protocol (it will present a Master Curriculum overview and wait for you to say `START PHASE 1`).
- If you are studying solo, read `androiddevelopmentlearningpath.md` first for the map, then work through `phase1kotlin.md` → `phase13releaseengineeringandproductionplaybook.md` in order, doing every exercise and checkpoint before moving on.
- Anything in this file set marked `[Extension]` is supplementary material added beyond the original source content — treat the unmarked content as the authoritative original curriculum.
