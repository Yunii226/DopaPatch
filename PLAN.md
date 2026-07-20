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
**Device-verified by user:** sign-up → confirm → sign-in works. (Confirmation email redirects
to `localhost:3000` which a phone can't open, but the account is confirmed anyway — see
`supabase/README.md` "Email confirmation" for turning it off in dev.)

### [x] Phase 3 · Data layer (offline-first)  — *Opus · high*  ⭐ backbone  ✅ DONE
**Depends on:** 0, 1, 2.
- Room: `TaskEntity`, `CompletionEntity`, `DailyNoteEntity`, `NoteImageEntity`; DAOs; `java.time` TypeConverters; `DopaPatchDb`.
- Supabase DTOs + entity⇄dto mappers.
- `TaskRepository` / `NoteRepository`: expose `Flow` **from Room**; write local first, mark dirty.
- `SyncManager` (WorkManager): pull rows changed since `lastSyncAt`, push dirty rows, honor soft-delete (`deleted_at`), last-write-wins on `updated_at`. Trigger on app open + periodic.
**Done when:** create a task offline, it appears from Room instantly; on reconnect it lands in Supabase; a row changed in Supabase shows up locally after sync. Leave a unit test for the mappers + a merge/conflict self-check.
**Handoff note:** Deps added — Room `2.7.1` (+KSP applied), `postgrest-kt`, WorkManager `2.10.0`,
kotlin-serialization plugin. **⚠️ `gradle.properties` now sets `android.disallowKotlinSourceSets=false`**
— required for KSP's generated sources under AGP 9 built-in Kotlin; don't remove it.
Layout: `data/local` (entities/daos/`Converters`/`DopaPatchDb`, java.time↔primitive converters,
`exportSchema=false`), `data/remote` (`Dtos` snake_case @Serializable + `Mappers` — timestamps
parsed via `OffsetDateTime` since PG sends offsets), `data/repository` (`TaskRepository`,
`NoteRepository` — Flow from Room, every write stamps `updatedAt=now`+`dirty=true`),
`data/sync` (`Merge.shouldApplyRemote` pure LWW, `SyncManager` push-then-pull for **tasks +
daily_notes**, `SyncPrefs` SharedPreferences cursor, `SyncWorker` one-time on open + 15-min periodic).
`AppContainer` now takes `Context`, installs `Postgrest`, owns db/repos/syncManager; `DopaPatchApp`
schedules sync on launch. **Sync entities `TaskEntity`/`DailyNoteEntity` carry `dirty`/`deletedAt`;
`upsert(...).toDto()` stamps `userId` for RLS on push.**
**Deferred (ponytail, wired when their feature lands):** completion sync → Phase 4/5 (needs
ToggleCompletion + a tombstone since `task_completions` has no server soft-delete), note_images
sync → Phase 8. Cursor is a single client clock (skew risk noted in code; overlap re-pulls are idempotent).
**Verified:** `:app:testDebugUnitTest` green — mapper round-trips (task+note, incl. soft-delete)
and LWW `shouldApplyRemote` (strictly-newer / tie / null-local). **Not device-verified by me**
(no emulator): the offline-create → reconnect → lands-in-Supabase round trip is yours to run.

### [x] Phase 4 · Domain: recurrence + time-blocks  — *Opus · high*  ✅ DONE
**Depends on:** 3.
- `RecurrenceExpander` using `lib-recur`: given a task's `rrule`+`dtstart` and a date (or range), return occurrences. Pure Kotlin.
- `TimeBlock` enum + `timeBlockOf(LocalTime?)` using the constant boundaries from `CLAUDE.md`.
- Use cases: `GetTasksForDate(date)` (recurrent occurrences + events, joined with completions) and `ToggleCompletion(taskId, date)`.
**Done when:** unit tests cover: daily/weekly/interval/until RRULEs, an event on its date only, and toggling completion for one day not affecting another.
**Handoff note:** Dep `org.dmfs:lib-recur:0.16.0`. Pure domain under `domain/`:
`recurrence/RecurrenceExpander` (`occursOn`/`occurrences`, lib-recur `DateTime` months are 0-based,
iteration capped at 10k, bad RRULE → false so the checklist can't crash), `timeblock/TimeBlock`+
`TimeBlocks.of` (boundaries constant, `<` comparisons: 00–05 Night/05–12 Morning/12–17 Afternoon/
17–21 Evening/21–24 Night), `model/DayTasks` (`DayTask`, `activeOn`, pure `buildDayTasks` join —
reuses `TaskEntity`, no separate domain model; `KIND_RECURRENT`/`KIND_EVENT` consts).
Use cases in `domain/usecase/UseCases`: `GetTasksForDate` combines task Flow + completion Flow;
`ToggleCompletion` → `CompletionRepository.toggle` (insert / revive-or-tombstone by flipping
`deleted`+`dirty`). `AppContainer` exposes `completionRepository`, `getTasksForDate`, `toggleCompletion`.
**⚠️ Completion network sync still deferred to Phase 5** — toggles write local dirty rows ready to push.
**Verified:** `:app:testDebugUnitTest` green — 7 domain tests (daily/weekly-BYDAY/interval/until,
event-only-on-date, per-day completion independence, time-block boundaries).

### [x] Phase 5 · Checklist view  — *Sonnet · medium* (RRULE picker: *Opus · high*)  ✅ DONE
**Depends on:** 3, 4.
- Screen with two sections — **Recurrent** and **Added** — checkboxes call `ToggleCompletion`, completed items get struck-through/dimmed, live day counter ("3/8 done").
- FAB → Add/Edit Task sheet: title, description, kind, date/time, alarm toggle (stored, wired in Phase 7), and a **basic RRULE builder** (daily / weekly-by-weekday / every-N + optional end date) that emits an RFC-5545 string.
- Swipe-to-delete (soft delete) + edit.
**Done when:** you can add a recurring habit and a one-off event, they appear in the right sections for the right days, and checking persists across relaunch + sync.
**Handoff note:** `ui/checklist/` — `ChecklistViewModel` (StateFlow from `GetTasksForDate`, splits
Recurrent/Added, done/total counts; toggle/save/delete each fire a quiet `syncManager.sync()`),
`ChecklistScreen` (two `LazyColumn` sections, counter in the top bar, `SwipeToDismissBox`
end→start = soft delete, tap row = edit, FAB = add, empty state), `AddEditTaskSheet`
(`ModalBottomSheet` form: title/description, Recurring↔One-off segmented, `DatePicker`/`TimePicker`
dialogs, alarm switch gated on a time, and the `RecurrenceEditor`). Builder logic is pure in
`domain/recurrence/RruleBuilder` — `buildRrule(type,weekdays,interval,until)` emits RFC-5545,
`parseRrule` seeds the editor when editing (round-trips its own output). **Completion network sync
now wired** into `SyncManager` (push dirty: upsert or delete-by-tombstone; pull by `completed_at`,
reconciling id clashes on unique(task,day)); `CompletionDao.getDirty` added. `MainActivity`
authenticated branch = `ChecklistScreen` (sign-out moved into its top bar); placeholder `MainScreen` gone.
**Verified:** `:app:testDebugUnitTest` green — 12 tests (added rrule build + parse round-trip).
**Not device-verified by me:** the add-habit/add-event → right sections → check-persists-across-relaunch+sync
flow is yours to run. Note: minSdk-24 `DatePicker`/`TimePicker` + swipe use one deprecated-but-working
`confirmValueChange` callback (kept deliberately — replacement is far more code).

### [x] Phase 6 · Time-blocks view  — *Sonnet · medium*  ✅ DONE
**Depends on:** 4, 5.
- Same day's tasks grouped by `TimeBlock` (Anytime, Morning…Night), each block a labeled section with its tasks; **current block highlighted**; tap a task to edit; check to complete.
- Top-level tab/nav switch between Checklist and Time-blocks (+ date selector for the day).
**Done when:** a task at 09:00 shows under Morning, an untimed task under Anytime, and the block containing `now` is visually marked.
**Handoff note:** Refactored the single-screen checklist into a shared parent + stateless bodies.
`ChecklistViewModel` → **`DayViewModel`** (adds `date` nav: `prevDay`/`nextDay`/`goToday`, and
`all` to `DayUiState`; drives both views). `DayScreen` = the top-level Scaffold: `TabRow`
(Checklist | Time-blocks), a `DateBar` (‹ / Today-or-date / ›), shared FAB + `AddEditTaskSheet`,
sign-out. `ChecklistBody` (was ChecklistScreen; two sections + swipe-delete) and `TimeBlocksBody` share
`TaskRowContent`. `MainActivity` → `DayScreen`.
**Time-blocks redesigned (user request) → Google-Calendar day view:** `TimeBlocksBody` now renders
an **all-day strip** (untimed tasks as toggle `FilterChip`s) over a **scrollable 24h hour grid**
(`HOUR_HEIGHT`/`LABEL_WIDTH` constants); timed tasks are `Surface` blocks positioned by
`offset(y = HOUR_HEIGHT * startMin/60)`, height ∝ `durationMin` (default 60, min 46dp), tap-to-edit
with an inner `Checkbox`; a red **now-line** and auto-scroll-to-now show only when viewing today.
ponytail: overlapping tasks stack (no column-splitting) — fine for a personal app. The old
Anytime→Night bucket grouping is gone; `TimeBlocks.of` still backs any future use.
**Verified:** build green; 12 unit tests unaffected (UI-only change). **Not device-verified by me:**
timeline placement/now-line + tab + date paging are yours to eyeball.

> **🎯 MVP complete after Phase 6.** App is usable daily: add habits/events, see them by time-block or checklist, check them off, synced across devices.
> ✅ **MVP DONE** — Phases 0–6 all shipped, build green, on-device checklist confirmed by user.

---

## Later increments — Phases 7–13

### [x] Phase 7 · Alarms  — *Opus · high*  ✅ DONE (needs on-device verify)
**Depends on:** 5.
- Schedule `setExactAndAllowWhileIdle` for events/occurrences with a `scheduled_time` + alarm enabled; notification channel; full-screen or heads-up notification with "Done"/"Snooze".
- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` + `POST_NOTIFICATIONS` perms with runtime request + rationale.
- `BOOT_COMPLETED` receiver reschedules; reschedule on task edit/sync.
**Done when:** an event fires at its time, survives reboot, and cancels when the task is deleted/completed.
**Handoff note:** Pure `domain/alarm/nextOccurrence(task, from)` (event once; recurrent via RRULE,
366-day horizon) — 3 unit tests. `data/alarm/`: `AlarmScheduler` (one alarm per task keyed by
`id.hashCode()`; `setExactAndAllowWhileIdle`, inexact fallback when `!canExact()`; `schedule`/
`scheduleAll`/`snooze`/`cancel`), `Notifications` (high-importance channel, heads-up notif with
Done/Snooze `PendingIntent`s, POST_NOTIFICATIONS-gated), `AlarmReceiver` (FIRE→notify+reschedule
next; DONE→`markDone`+cancel+sync; SNOOZE→+10min; `goAsync`+IO coroutine), `BootReceiver`
(BOOT_COMPLETED→`rescheduleAlarms`). `CompletionRepository.markDone` (idempotent, never un-checks).
`TaskDao.alarmTasks()`. `AppContainer.alarmScheduler`/`rescheduleAlarms`/`rescheduleTask`.
Reschedule hooked into `DayViewModel.save`/`delete` + `SyncWorker` (after sync) + boot;
`DopaPatchApp` ensures the channel. Manifest: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED,
SCHEDULE_EXACT_ALARM (31-32) + **USE_EXACT_ALARM** (auto-grants exactness 33+, so no settings dance)
+ both receivers. `DayScreen` requests POST_NOTIFICATIONS once on 33+.
**Ceilings (ponytail):** heads-up notif (not full-screen — no USE_FULL_SCREEN_INTENT); inexact
fallback if exact somehow denied. **⚠️ Not device-verified by me** (no device/reboot here): the
fires-at-time / survives-reboot / cancels-on-delete-or-complete checks are yours to run.

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
