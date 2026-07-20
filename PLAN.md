# DopaPatch — Build Plan

Read `CLAUDE.md` first (stack, architecture, domain rules). This file is the **task board**:
each phase is a self-contained unit an agent can pick up. Do phases roughly in order; respect
`Depends on`. Tick the box and add a handoff note when done.

## Decisions locked with the user
- Backend: **Supabase from day 1** (Postgres + Auth + Storage), offline-first with Room cache.
- Recurrence: **full RRULE** (RFC-5545) via `lib-recur`.
- AI beautify: **Gemini free tier** (`gemini-1.5-flash`), user supplies their own API key.
- **MVP = Time-blocks + Checklist** (Phases 0–6). Everything after is a later increment.

## Model / effort guide
- **Opus · high** — architecture-critical or fiddly platform work (deps, sync, recurrence, alarms, widgets, AI). Get it right once.
- **Sonnet · medium** — standard screens, viewmodels, straightforward features.
- **Haiku/Sonnet · low** — pure boilerplate, resources, mechanical refactors.

---

## MVP — Phases 0–6

### [x] Phase 0 · Foundation & config  — *Opus · high*  ✅ DONE
**Depends on:** none.
Wire up the DI + build plumbing everything else needs. **Feature deps are added per-phase**
(Room→P3, Supabase-kt+Ktor→P2/3, lib-recur→P4, Coil→P8, WorkManager→P3) so each dep is
validated by code that uses it — do NOT front-load them here.
Done in this repo:
- Catalog + `app/build.gradle.kts`: coroutines `1.10.2`, lifecycle-viewmodel-compose (2.6.1), `coreLibraryDesugaring` (desugar_jdk_libs `2.1.5`, needed for `java.time` on minSdk 24). KSP `2.2.10-2.0.2` is pinned in the catalog but **not applied yet** — Phase 3 applies it for Room.
- **Manual DI** (no Hilt): `di/AppContainer` holds app singletons, created in `DopaPatchApp` (registered in manifest). ViewModels get deps via `viewModelFactory { initializer { … app.container … } }` — see `HomeViewModel`.
- `BuildConfig` fields `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GEMINI_API_KEY` from `local.properties` (empty fallback so fresh clones build). See `local.properties.example`.
- `di/AppContainer` exposes `AppConfig`; placeholder `HomeViewModel` + `HomeScreen` exercise the DI + BuildConfig chain.
**⚠️ Notes for all agents:**
- AGP 9.2.1 uses **built-in Kotlin** — there is NO `org.jetbrains.kotlin.android` plugin. Don't add one.
- **Hilt does NOT work with AGP 9** (google/dagger#4944 — "Android BaseExtension not found"). Use the manual `AppContainer` pattern; don't reintroduce Hilt/Dagger.
**Handoff note:** placeholder `ui/home/HomeScreen` + `HomeViewModel` are throwaway — Phase 2/5 replace them with real auth-gated nav.

### [x] Phase 1 · Supabase backend  — *Sonnet · medium* (or user-run)  ✅ ARTIFACTS DONE
**Depends on:** 0.
- Apply `supabase/schema.sql` (tables, RLS, `updated_at` triggers) in the Supabase SQL editor.
- Create Storage bucket `note-images` (private) with per-user RLS.
- Document in `supabase/README.md`: creating the project, where the URL + anon key go.
**Done when:** schema applied, a manually-inserted row is only visible to its owner.
**Handoff note:** `schema.sql` now creates the `note-images` bucket + `note_images_owner`
policy in SQL too (no dashboard clicking) and is idempotent (drop-then-create policies,
`on conflict do nothing` bucket). `supabase/README.md` has the full setup + an RLS
verification snippet. **⚠️ User action still required:** create the Supabase project, run
the script, and fill `SUPABASE_URL`/`SUPABASE_ANON_KEY` in `local.properties` — an agent
can't provision the backend. Phase 2 (auth) needs those keys populated.

### [x] Phase 2 · Auth  — *Sonnet · medium*  ✅ DONE
**Depends on:** 0, 1.
- Supabase email/password auth via `auth-kt`; persist session; auto-login on relaunch.
- Sign-in / sign-up screen; sign-out in a stub settings menu.
- Gate the app: no session ⇒ auth screen, else main.
**Done when:** you can sign up, kill the app, reopen, and still be logged in.
**Handoff note:** Deps added to catalog — `supabase-bom 3.1.4`, `auth-kt`, `ktor-client-okhttp 3.1.1`;
`INTERNET` perm added. `AppContainer.supabase` (lazy, guarded by `config.isBackendConfigured`)
installs `Auth` — session **auto-persists to SharedPreferences + auto-refreshes** (no manual
session manager). `ui/auth/AuthViewModel` + `AuthScreen` do email/password sign-in/up (handles
email-confirmation-on = "check your email"). `MainActivity.DopaPatchRoot` gates on
`auth.sessionStatus`: Initializing→spinner, Authenticated→`MainScreen` (placeholder + **Sign out**
in the top bar = the stub settings menu), else→`AuthScreen`. `HomeViewModel` now holds
`userEmail`+`signOut()`. **⚠️ If your Supabase project requires email confirmation**, sign-up
won't create a live session until you click the emailed link — disable confirmation in
Auth settings for faster dev testing. Uses the new `sb_publishable_` key; worked fine.
**Not yet device-verified** by me (no emulator here) — the "kill & reopen still logged in"
check is on you.

### [ ] Phase 3 · Data layer (offline-first)  — *Opus · high*  ⭐ backbone
**Depends on:** 0, 1, 2.
- Room: `TaskEntity`, `CompletionEntity`, `DailyNoteEntity`, `NoteImageEntity`; DAOs; `java.time` TypeConverters; `DopaPatchDb`.
- Supabase DTOs + entity⇄dto mappers.
- `TaskRepository` / `NoteRepository`: expose `Flow` **from Room**; write local first, mark dirty.
- `SyncManager` (WorkManager): pull rows changed since `lastSyncAt`, push dirty rows, honor soft-delete (`deleted_at`), last-write-wins on `updated_at`. Trigger on app open + periodic.
**Done when:** create a task offline, it appears from Room instantly; on reconnect it lands in Supabase; a row changed in Supabase shows up locally after sync. Leave a unit test for the mappers + a merge/conflict self-check.

### [ ] Phase 4 · Domain: recurrence + time-blocks  — *Opus · high*
**Depends on:** 3.
- `RecurrenceExpander` using `lib-recur`: given a task's `rrule`+`dtstart` and a date (or range), return occurrences. Pure Kotlin.
- `TimeBlock` enum + `timeBlockOf(LocalTime?)` using the constant boundaries from `CLAUDE.md`.
- Use cases: `GetTasksForDate(date)` (recurrent occurrences + events, joined with completions) and `ToggleCompletion(taskId, date)`.
**Done when:** unit tests cover: daily/weekly/interval/until RRULEs, an event on its date only, and toggling completion for one day not affecting another.

### [ ] Phase 5 · Checklist view  — *Sonnet · medium* (RRULE picker: *Opus · high*)
**Depends on:** 3, 4.
- Screen with two sections — **Recurrent** and **Added** — checkboxes call `ToggleCompletion`, completed items get struck-through/dimmed, live day counter ("3/8 done").
- FAB → Add/Edit Task sheet: title, description, kind, date/time, alarm toggle (stored, wired in Phase 7), and a **basic RRULE builder** (daily / weekly-by-weekday / every-N + optional end date) that emits an RFC-5545 string.
- Swipe-to-delete (soft delete) + edit.
**Done when:** you can add a recurring habit and a one-off event, they appear in the right sections for the right days, and checking persists across relaunch + sync.

### [ ] Phase 6 · Time-blocks view  — *Sonnet · medium*
**Depends on:** 4, 5.
- Same day's tasks grouped by `TimeBlock` (Anytime, Morning…Night), each block a labeled section with its tasks; **current block highlighted**; tap a task to edit; check to complete.
- Top-level tab/nav switch between Checklist and Time-blocks (+ date selector for the day).
**Done when:** a task at 09:00 shows under Morning, an untimed task under Anytime, and the block containing `now` is visually marked.

> **🎯 MVP complete after Phase 6.** App is usable daily: add habits/events, see them by time-block or checklist, check them off, synced across devices.

---

## Later increments — Phases 7–13

### [ ] Phase 7 · Alarms  — *Opus · high*
**Depends on:** 5.
- Schedule `setExactAndAllowWhileIdle` for events/occurrences with a `scheduled_time` + alarm enabled; notification channel; full-screen or heads-up notification with "Done"/"Snooze".
- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` + `POST_NOTIFICATIONS` perms with runtime request + rationale.
- `BOOT_COMPLETED` receiver reschedules; reschedule on task edit/sync.
**Done when:** an event fires at its time, survives reboot, and cancels when the task is deleted/completed.

### [ ] Phase 8 · Daily note editor  — *Opus · high*
**Depends on:** 3.
- One Markdown doc per day. Toolbar over `BasicTextField` inserts Markdown around the selection: **bold**, *italic*, headings, bullet/number lists, checkboxes. Live/preview render via richtext-commonmark.
- Insert images: gallery (`PickVisualMedia`) + camera (`TakePicture`+FileProvider) → upload to Supabase Storage `note-images` → embed `![](path)`; render with Coil.
- Autosave to Room (debounced) → sync.
**Done when:** text + formatting + an image persist for the day, survive relaunch, and sync. Ceiling: toolbar-Markdown, not WYSIWYG (documented).

### [ ] Phase 9 · Note export to `.md`  — *Sonnet · low*
**Depends on:** 8.
- Export the day's (or a range's) note as `.md` via SAF / share sheet; bundle referenced images (resolve storage paths → relative files, or inline links). 
**Done when:** exported file opens correctly in any Markdown reader.

### [ ] Phase 10 · Voice input  — *Sonnet · medium*
**Depends on:** 8.
- Mic button in the note editor: `SpeechRecognizer` (RECORD_AUDIO perm) → insert transcript at cursor.
**Done when:** speaking inserts recognized text into the note.

### [ ] Phase 11 · AI beautify  — *Opus · high*
**Depends on:** 8.
- "Tidy" action: send note Markdown to Gemini `gemini-1.5-flash` (key from BuildConfig) with a prompt to reorganize into clean, readable Markdown **without inventing content**; show a diff/preview before replacing; undo.
- Handle no-key / rate-limit / offline gracefully.
**Done when:** a messy note comes back reorganized, user can accept or reject, and a missing key shows a friendly message instead of crashing.

### [ ] Phase 12 · Widgets  — *Opus · high*
**Depends on:** 4, 5.
- Glance widgets: **today's checklist** (checkable) and **current/next time-block**. Responsive small→large so phone and tablet both look right. Quick-add deep link. Update on data change + periodic.
**Done when:** widget shows today's tasks, checking on the widget updates the app and vice-versa, and it reflows on a tablet.

### [ ] Phase 13 · Polish & motivation  — *Sonnet · medium*
**Depends on:** 6.
- Settings (time-block boundaries, theme, account). Streaks + daily completion %, subtle haptic/confetti on clearing the day. Empty states, dark theme, a11y pass.
**Done when:** settings persist, streak counts correctly, and the app feels finished.

---

## Proposed extras (optional — ponytail: only if wanted, all cheap)
- **Carry-over** unfinished *added* tasks to the next day (one toggle in settings).
- **Focus mode**: dim everything except the current time-block.
- **Quick-add from widget/notification** without opening the app.
- **Weekly review** screen: completion rate per habit (reads existing `task_completions`, no new data).

## How to run
`./gradlew :app:assembleDebug` to build. Fill `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GEMINI_API_KEY`
in `local.properties` (see `local.properties.example`) before running against a real backend.
