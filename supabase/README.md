# DopaPatch — Supabase setup

One-time backend setup. Everything the app needs lives in [`schema.sql`](./schema.sql):
tables, RLS, `updated_at` triggers, the `note-images` Storage bucket, and its per-user
policy. The script is idempotent — safe to re-run after edits.

## 1. Create the project
1. Sign in at [supabase.com](https://supabase.com) → **New project** (free tier is fine).
2. Pick a region near you and set a database password.
3. Wait for it to provision (~2 min).

## 2. Apply the schema
1. Open **SQL Editor** → **New query**.
2. Paste the full contents of `schema.sql` → **Run**.
3. Expect "Success. No rows returned." Confirm under **Table Editor** that
   `tasks`, `task_completions`, `daily_notes`, `note_images` exist, and under
   **Storage** that the private `note-images` bucket exists.

## 3. Wire the keys into the app
1. In the dashboard: **Project Settings → API**.
2. Copy **Project URL** and the **anon / public** key.
3. In the repo root, put them in `local.properties` (git-ignored — see
   `local.properties.example`):
   ```
   SUPABASE_URL=https://<your-ref>.supabase.co
   SUPABASE_ANON_KEY=<anon-key>
   ```
   These become `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY`. Never commit them.

## 4. Verify RLS (the "done when")
RLS is enabled on every table; a user only ever sees rows where `user_id = auth.uid()`.
Quick check in the SQL editor:

```sql
-- 1. Create two users via Authentication → Users (or the app's sign-up).
-- 2. Insert a task as user A (replace the uuid with A's id from auth.users):
insert into tasks (user_id, title, kind, rrule, dtstart)
  values ('<user-A-uuid>', 'test habit', 'recurrent', 'FREQ=DAILY', current_date);

-- 3. Impersonate each user and confirm visibility:
set request.jwt.claims = '{"sub":"<user-A-uuid>","role":"authenticated"}';
select count(*) from tasks;   -- expect 1

set request.jwt.claims = '{"sub":"<user-B-uuid>","role":"authenticated"}';
select count(*) from tasks;   -- expect 0  ← RLS isolating owners

reset request.jwt.claims;
```

Deleting the task afterward: `delete from tasks where title = 'test habit';` (run with
claims reset, or as the service role, since RLS now blocks anonymous deletes).

## Notes
- Storage object paths are `<user_id>/<note_id>/<file>`; the `note_images_owner` policy
  keys off the first path segment, so users only touch their own folder.
- Soft-delete (`deleted_at`) + `updated_at` drive offline-first sync (Phase 3);
  nothing here is hard-deleted by the app.
