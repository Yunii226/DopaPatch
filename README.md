# DopaPatch

A dopamine-regulator Android app that helps you get your shit done. Plan your day as
**time-blocks** or a **checklist**, keep recurring habits and one-off events, get alarms,
write a rich **daily note** (Markdown, images, voice), export to `.md`, and let AI tidy it up.

Kotlin · Jetpack Compose · Material 3 · Supabase · offline-first. Free tools only.

## Status

Under active development by AI agents. See **[PLAN.md](PLAN.md)** for the phased roadmap
and **[CLAUDE.md](CLAUDE.md)** for the stack, architecture, and working rules.

- [x] **Phase 0** — Foundation: toolchain, manual DI, BuildConfig secrets
- [ ] Phase 1 — Supabase schema & RLS
- [ ] Phase 2 — Auth
- [ ] Phase 3 — Offline-first data layer (Room ⇄ Supabase)
- [ ] Phase 4 — Recurrence (RRULE) + time-blocks
- [ ] Phase 5 — Checklist view · [ ] Phase 6 — Time-blocks view — **← MVP**
- [ ] Phases 7–13 — Alarms · Daily notes · `.md` export · Voice · AI beautify · Widgets · Polish

## Stack

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Build | AGP 9.2.1, Kotlin 2.2.10, minSdk 24 / target 36 |
| Backend | Supabase (Postgres + Auth + Storage), offline-first with Room |
| Recurrence | `lib-recur` (RFC-5545 RRULE) |
| AI | Gemini `gemini-1.5-flash` (free tier) |
| DI | Manual `AppContainer` (Hilt is incompatible with AGP 9) |

## Getting started

1. Open in Android Studio (the one that generated this project — AGP 9.2.1).
2. Copy the keys from **[`local.properties.example`](local.properties.example)** into your
   `local.properties` (git-ignored):
   - `SUPABASE_URL`, `SUPABASE_ANON_KEY` — from your Supabase project → Settings → API
   - `GEMINI_API_KEY` — free key from https://aistudio.google.com/app/apikey
3. Apply the database schema in the Supabase SQL editor: **[`supabase/schema.sql`](supabase/schema.sql)**.
4. Build & run:
   ```
   ./gradlew :app:assembleDebug
   ```
   The app builds without any keys (features that need them just stay disabled).

## Project layout

```
app/src/main/java/com/example/dopapatch/
  data/    Room, Supabase, repositories, sync
  domain/  models, recurrence, time-blocks, use cases (pure Kotlin)
  ui/      <feature>/ screens + viewmodels ; theme/ ; nav/
  di/      AppContainer (manual singletons)
supabase/  schema.sql (+ setup notes)
```
