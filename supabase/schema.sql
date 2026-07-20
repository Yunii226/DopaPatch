-- DopaPatch — Supabase schema. Run in the Supabase SQL editor.
-- Single-user-per-account model: every row is scoped to auth.uid() via RLS.
-- Soft-delete (deleted_at) + updated_at drive offline-first sync (last-write-wins).

-- ---------- helper: touch updated_at ----------
create or replace function set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end $$;

-- ---------- tasks (recurrent habits + one-off events) ----------
create table if not exists tasks (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  title         text not null check (length(title) between 1 and 300),
  description   text,
  kind          text not null check (kind in ('recurrent','event')),
  scheduled_date date,          -- one-off events; null for pure recurrent
  scheduled_time time,          -- nullable; presence => time-block + alarm eligible
  duration_min  int check (duration_min is null or duration_min > 0),
  rrule         text,           -- RFC-5545 RRULE for recurrent; null for events
  dtstart       date,           -- recurrence anchor for recurrent tasks
  alarm_enabled boolean not null default false,
  sort_order    int not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted_at    timestamptz,    -- soft delete
  -- shape guard: recurrent needs rrule+dtstart; event needs scheduled_date
  constraint task_shape check (
    (kind = 'recurrent' and rrule is not null and dtstart is not null) or
    (kind = 'event' and scheduled_date is not null)
  )
);
create index if not exists tasks_user_idx    on tasks(user_id);
create index if not exists tasks_synced_idx  on tasks(user_id, updated_at);
create trigger tasks_touch before update on tasks
  for each row execute function set_updated_at();

-- ---------- completions (per occurrence, per day) ----------
create table if not exists task_completions (
  id           uuid primary key default gen_random_uuid(),
  task_id      uuid not null references tasks(id) on delete cascade,
  user_id      uuid not null references auth.users(id) on delete cascade,
  occurred_on  date not null,               -- the day this occurrence belongs to
  completed_at timestamptz not null default now(),
  unique (task_id, occurred_on)
);
create index if not exists completions_user_idx on task_completions(user_id, occurred_on);

-- ---------- daily notes (one Markdown doc per user per day) ----------
create table if not exists daily_notes (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users(id) on delete cascade,
  note_date  date not null,
  content_md text not null default '',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, note_date)
);
create index if not exists notes_synced_idx on daily_notes(user_id, updated_at);
create trigger notes_touch before update on daily_notes
  for each row execute function set_updated_at();

-- ---------- note images (refs into the note-images Storage bucket) ----------
create table if not exists note_images (
  id           uuid primary key default gen_random_uuid(),
  note_id      uuid not null references daily_notes(id) on delete cascade,
  user_id      uuid not null references auth.users(id) on delete cascade,
  storage_path text not null,               -- path within bucket 'note-images'
  created_at   timestamptz not null default now()
);

-- ---------- Row Level Security: a user sees only their own rows ----------
alter table tasks            enable row level security;
alter table task_completions enable row level security;
alter table daily_notes      enable row level security;
alter table note_images      enable row level security;

do $$
declare t text;
begin
  foreach t in array array['tasks','task_completions','daily_notes','note_images'] loop
    execute format($p$
      create policy %1$s_owner on %1$s
        for all to authenticated
        using (user_id = auth.uid())
        with check (user_id = auth.uid());
    $p$, t);
  end loop;
end $$;

-- ---------- Storage bucket ----------
-- Create bucket 'note-images' (private) in the dashboard, then add a Storage policy
-- restricting objects to a per-user prefix, e.g. path like: <user_id>/<note_id>/<file>.
-- Policy (Storage > Policies): (bucket_id = 'note-images' and (storage.foldername(name))[1] = auth.uid()::text)
