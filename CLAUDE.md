# DopaPatch — Agent Guide

A dopamine-regulator Android app: see your day as time-blocks or a checklist, keep
recurring habits + one-off events, get alarms, write a rich daily note, export to `.md`,
dictate by voice, and have AI tidy the note. Personal single-user app, free tools only.

**This file is the contract for every agent working on the repo. Read it before touching code.**

## Stack (already chosen — do not swap without asking the user)

| Concern | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose + Material 3 | Project already scaffolded |
| Build | AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01 | `gradle/libs.versions.toml` is the version catalog — add deps there, not inline |
| SDK | minSdk 24, target/compile 36, Java 11 | minSdk 24 ⇒ **core library desugaring required** for `java.time` |
| DI | **Manual** (`AppContainer` + `viewModelFactory`) | Hilt's Gradle plugin is incompatible with AGP 9 (google/dagger#4944). Single-module app — no DI framework needed. Don't add Hilt/Koin. |
| Async | Coroutines + Flow | UI observes `StateFlow` |
| Local DB (source of truth) | Room | UI reads Room, never the network directly |
| Backend | Supabase (Postgres + Auth + Storage + Realtime) via `supabase-kt` | Free tier |
| Sync | Offline-first: Room ⇄ Supabase, last-write-wins on `updated_at`, soft-delete | WorkManager |
| Recurrence | `org.dmfs:lib-recur` (RFC-5545 RRULE) | Do NOT hand-roll recurrence math |
| Navigation | Navigation-Compose, type-safe routes (kotlinx.serialization) | |
| Images | Coil (load), `PickVisualMedia` (gallery), `TakePicture`+FileProvider (camera) | |
| Markdown render | `com.halilibo.compose-richtext:richtext-commonmark` | Notes stored AS Markdown text |
| Voice | Android `SpeechRecognizer` (on-device, free) | needs `RECORD_AUDIO` |
| AI beautify | Gemini `gemini-1.5-flash` REST, free tier | key via BuildConfig, never committed |
| Alarms | `AlarmManager.setExactAndAllowWhileIdle` + notification + `BOOT_COMPLETED` receiver | |
| Widgets | Jetpack Glance | phone + tablet responsive |

## Architecture

Single Gradle module (`:app`) for now — **do not split into modules speculatively.**
Package by feature under `com.example.dopapatch`:

```
data/        Room (entity, dao, db), Supabase (dto, api), repository, sync
domain/      models, recurrence, timeblock, use cases (pure Kotlin, unit-testable)
ui/          <feature>/  screen + viewmodel + components ;  theme/ ; nav/
di/          AppContainer (manual singletons)
```

Rules:
- **Room is the single source of truth for the UI.** Repositories expose `Flow` from Room; sync writes into Room.
- ViewModels expose one `StateFlow<UiState>`; screens are stateless and hoist events up.
- Domain layer (recurrence, time-blocks, use cases) is pure Kotlin with **no Android imports** so it unit-tests without a device.
- Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GEMINI_API_KEY`) live in `local.properties` → exposed as `BuildConfig` fields. `local.properties` is git-ignored. Never hardcode or commit keys.

## Working agreement for agents

1. **Pick a task from `PLAN.md`.** Check its `Depends on` phases are done before starting.
2. Stay in scope. Ship the phase, not the next three. Ponytail rules apply: laziest thing that works.
3. Every non-trivial logic (recurrence, sync, time-blocks, parsing) leaves **one runnable check** —
   a `test_*` unit test or an `assert`-based self-check. No test frameworks beyond JUnit already present.
4. `./gradlew :app:assembleDebug` (and `:app:testDebugUnitTest` if you added domain logic) must pass before you call a phase done.
5. Commits go to `git@github.com:Yunii226/DopaPatch.git`. **Commit each phase when it is completed**
   (build passing), one phase per commit, conventional-commit style (e.g. `feat: phase 3 data layer`).
6. Update `PLAN.md`: tick the phase's checkbox and note anything the next agent needs.

## Domain rules (so everyone agrees)

- A **task** is either `kind = 'recurrent'` (has an `rrule` + `dtstart`) or `kind = 'event'` (one-off, has `scheduled_date`). Both may have a `scheduled_time`.
- **Completion is per-occurrence-per-day**: `task_completions(task_id, occurred_on)`. Checking a recurring task on Tuesday does not affect Wednesday.
- The **Checklist** has exactly two sections: *Recurrent* (today's expanded RRULE occurrences) and *Added* (today's events). Matches the two `kind`s.
- **Time-blocks** are derived from `scheduled_time`, not stored. Default boundaries: Morning 05–12, Afternoon 12–17, Evening 17–21, Night 21–05; tasks with no time go to an **Anytime** block. Boundaries become a user setting in a later phase — keep them in one constant now.
- **Daily note**: one Markdown document per `(user, date)`. Formatting = Markdown syntax inserted by a toolbar over a `BasicTextField` (no WYSIWYG engine — that's the ceiling). Images are Supabase Storage refs embedded as `![](storage_path)`.

## Non-negotiables (do not "simplify" these away)

Input validation at trust boundaries · auth/RLS so a user only sees their own rows ·
error handling that prevents note/task data loss · accessibility basics (content descriptions,
touch targets) · never commit secrets.
