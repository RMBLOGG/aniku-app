-- =========================================================
-- FITUR: Quiz "Tebak Anime dari Poster"
-- Wajib punya clan buat main. Jawaban benar ngasih XP ke diri
-- sendiri PENUH, dan ke semua member clan lain SETENGAHNYA.
-- 10x/hari gratis, lebihnya potong Diamond (default 5000 DM/ronde).
-- =========================================================

-- =========================================================
-- BAGIAN 1: Tabel tracking pemakaian harian per user
-- =========================================================
create table if not exists public.quiz_daily_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  usage_date date not null default current_date,
  play_count integer not null default 0,       -- total soal yang udah dimainkan hari ini (gratis + bayar)
  correct_count integer not null default 0,    -- total jawaban benar hari ini
  perfect_bonus_given boolean not null default false,
  primary key (user_id, usage_date)
);

alter table public.quiz_daily_usage enable row level security;

-- User cuma boleh BACA baris pemakaian hariannya sendiri (buat nampilin
-- "sisa X soal gratis hari ini" di UI). Gak ada policy insert/update buat
-- client -- semua penulisan WAJIB lewat function di bawah.
create policy "User baca pemakaian quiz sendiri"
on public.quiz_daily_usage
for select
to authenticated
using (auth.uid() = user_id);


-- =========================================================
-- BAGIAN 2: Helper - recalc season_level dari season_xp
-- =========================================================
-- Kurva level sama persis kayak yang dipakai di app (Screens.kt):
--   XP dasar buat level L = 20 * (L-1)^2
-- Jadi level = floor(sqrt(season_xp / 20)) + 1
create or replace function public.recalc_season_level(p_user_id uuid)
returns void
language sql
security definer
set search_path = public
as $$
  update public.profiles
  set season_level = floor(sqrt(greatest(season_xp, 0) / 20.0))::integer + 1
  where id = p_user_id;
$$;


-- =========================================================
-- BAGIAN 3: Function play_quiz_round()
-- Dipanggil SEBELUM soal ditampilkan ke user - buat ngecek eligibility,
-- motong jatah harian/Diamond. Return info dipake buat nentuin apakah
-- app boleh nampilin soal atau nolak duluan.
-- =========================================================
create or replace function public.play_quiz_round(p_cost integer default 5000)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_clan_id uuid;
  v_balance integer;
  v_play_count integer;
  v_is_free boolean;
begin
  if v_user_id is null then
    raise exception 'Harus login dulu buat main quiz';
  end if;

  -- WAJIB punya clan. Dicek paling awal biar ga sempet motong jatah/Diamond
  -- kalau ternyata emang gak eligible.
  select clan_id into v_clan_id
  from public.clan_members
  where user_id = v_user_id
  limit 1;

  if v_clan_id is null then
    raise exception 'Kamu harus join clan dulu buat main quiz ini';
  end if;

  -- Kunci baris saldo user ini biar gak race condition kalau spam klik
  select diamond_balance into v_balance
  from public.profiles
  where id = v_user_id
  for update;

  if v_balance is null then
    raise exception 'Profil user gak ketemu';
  end if;

  -- Upsert + kunci baris pemakaian hari ini
  insert into public.quiz_daily_usage (user_id, usage_date, play_count, correct_count)
  values (v_user_id, current_date, 0, 0)
  on conflict (user_id, usage_date) do nothing;

  select play_count into v_play_count
  from public.quiz_daily_usage
  where user_id = v_user_id and usage_date = current_date
  for update;

  v_is_free := v_play_count < 10;

  if not v_is_free then
    if v_balance < p_cost then
      raise exception 'DM gak cukup buat main lagi. Saldo kamu %, butuh %', v_balance, p_cost;
    end if;

    update public.profiles
    set diamond_balance = diamond_balance - p_cost
    where id = v_user_id;

    v_balance := v_balance - p_cost;
  end if;

  update public.quiz_daily_usage
  set play_count = play_count + 1
  where user_id = v_user_id and usage_date = current_date;

  return jsonb_build_object(
    'is_free', v_is_free,
    'play_count_today', v_play_count + 1,
    'free_remaining', greatest(0, 10 - (v_play_count + 1)),
    'remaining_balance', v_balance
  );
end;
$$;

revoke all on function public.play_quiz_round(integer) from public;
grant execute on function public.play_quiz_round(integer) to authenticated;


-- =========================================================
-- BAGIAN 4: Function submit_quiz_answer()
-- Dipanggil SETELAH user milih jawaban. Kalau bener, kasih XP ke diri
-- sendiri (penuh) + semua member clan lain (setengah). Kalau salah,
-- gak ada XP sama sekali, cuma nyatet buat perfect-bonus tracking.
-- =========================================================
create or replace function public.submit_quiz_answer(
  p_correct boolean,
  p_fast boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_clan_id uuid;
  v_self_xp integer := 0;
  v_mate_xp integer := 0;
  v_play_count integer;
  v_correct_count integer;
  v_perfect_bonus_given boolean;
  v_perfect_bonus_awarded boolean := false;
  v_mate_ids uuid[];
begin
  if v_user_id is null then
    raise exception 'Harus login dulu buat main quiz';
  end if;

  select clan_id into v_clan_id
  from public.clan_members
  where user_id = v_user_id
  limit 1;

  if v_clan_id is null then
    raise exception 'Kamu harus join clan dulu buat main quiz ini';
  end if;

  -- Kunci baris pemakaian hari ini (harusnya udah ada dari play_quiz_round)
  select play_count, correct_count, perfect_bonus_given
  into v_play_count, v_correct_count, v_perfect_bonus_given
  from public.quiz_daily_usage
  where user_id = v_user_id and usage_date = current_date
  for update;

  if v_play_count is null then
    raise exception 'Belum ada sesi quiz hari ini, panggil play_quiz_round dulu';
  end if;

  if p_correct then
    v_self_xp := case when p_fast then 150 else 100 end;
    v_mate_xp := v_self_xp / 2;

    -- XP ke diri sendiri (penuh)
    update public.profiles
    set season_xp = season_xp + v_self_xp
    where id = v_user_id;
    perform public.recalc_season_level(v_user_id);

    -- XP ke semua member clan lain (setengah), kunci dulu biar konsisten
    select array_agg(user_id) into v_mate_ids
    from public.clan_members
    where clan_id = v_clan_id and user_id <> v_user_id;

    if v_mate_ids is not null then
      update public.profiles
      set season_xp = season_xp + v_mate_xp
      where id = any(v_mate_ids);

      -- Recalc level satu-satu buat tiap clanmate yang kebagian XP
      perform public.recalc_season_level(mate_id)
      from unnest(v_mate_ids) as mate_id;
    end if;

    v_correct_count := v_correct_count + 1;
  end if;

  -- Perfect bonus: 10 soal PERTAMA hari ini semua bener (play_count = correct_count = 10)
  if not v_perfect_bonus_given and v_play_count = 10 and v_correct_count = 10 then
    update public.profiles
    set season_xp = season_xp + 500
    where id = v_user_id;
    perform public.recalc_season_level(v_user_id);

    if v_mate_ids is not null then
      update public.profiles
      set season_xp = season_xp + 250
      where id = any(v_mate_ids);

      perform public.recalc_season_level(mate_id)
      from unnest(v_mate_ids) as mate_id;
    end if;

    v_perfect_bonus_awarded := true;
  end if;

  update public.quiz_daily_usage
  set correct_count = v_correct_count,
      perfect_bonus_given = perfect_bonus_given or v_perfect_bonus_awarded
  where user_id = v_user_id and usage_date = current_date;

  return jsonb_build_object(
    'self_xp_awarded', v_self_xp,
    'mate_xp_awarded', v_mate_xp,
    'perfect_bonus_awarded', v_perfect_bonus_awarded,
    'correct_count_today', v_correct_count
  );
end;
$$;

revoke all on function public.submit_quiz_answer(boolean, boolean) from public;
grant execute on function public.submit_quiz_answer(boolean, boolean) to authenticated;
