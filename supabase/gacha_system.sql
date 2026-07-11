-- =========================================================
-- BAGIAN 1: Tabel koleksi karakter tiap user
-- =========================================================
create table if not exists public.user_characters (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  character_mal_id integer not null references public.characters(mal_id) on delete cascade,
  count integer not null default 1,          -- kalau dapet karakter yg sama lagi, count nambah (bukan row baru)
  obtained_at timestamptz default now(),
  last_obtained_at timestamptz default now()
);

-- 1 user cuma punya 1 row per karakter (duplikat nambah "count", bukan row baru)
create unique index if not exists idx_user_characters_unique
  on public.user_characters (user_id, character_mal_id);

create index if not exists idx_user_characters_user on public.user_characters (user_id);

alter table public.user_characters enable row level security;

-- User cuma boleh BACA koleksi miliknya sendiri. Gak ada policy insert/update/delete
-- buat role anon/authenticated -- semua penulisan WAJIB lewat function gacha_roll()
-- di bawah (yang jalan sebagai SECURITY DEFINER), biar gak bisa dicurangin dari client.
create policy "User baca koleksi sendiri"
on public.user_characters
for select
to authenticated
using (auth.uid() = user_id);


-- =========================================================
-- BAGIAN 2: Function gacha_roll()
-- =========================================================
-- ⚠️ SESUAIKAN DULU sebelum dijalankan:
-- Ganti "public.profiles" dan nama kolom "diamond_balance" di bawah
-- sesuai nama tabel/kolom saldo DM yang beneran ada di project Aniku kamu
-- (yang dipake sama fitur "Beri Diamond" kemarin).
create or replace function public.gacha_roll(p_cost integer default 50)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_balance integer;
  v_roll numeric;
  v_rarity text;
  v_character record;
  v_existing_count integer;
begin
  if v_user_id is null then
    raise exception 'Harus login dulu buat gacha';
  end if;

  -- Cek & kunci baris saldo user ini biar gak race condition kalau spam klik
  select diamond_balance into v_balance
  from public.profiles
  where id = v_user_id
  for update;

  if v_balance is null then
    raise exception 'Profil user gak ketemu';
  end if;

  if v_balance < p_cost then
    raise exception 'DM gak cukup. Saldo kamu % , butuh %', v_balance, p_cost;
  end if;

  -- Potong DM di awal
  update public.profiles
  set diamond_balance = diamond_balance - p_cost
  where id = v_user_id;

  -- Roll rarity (weighted random 0-100)
  v_roll := random() * 100;
  v_rarity := case
    when v_roll <= 1  then 'Mythic'      -- 1%
    when v_roll <= 5  then 'Legendary'   -- 4%
    when v_roll <= 17 then 'Epic'        -- 12%
    when v_roll <= 45 then 'Rare'        -- 28%
    else 'Common'                        -- 55%
  end;

  -- Pilih 1 karakter random dari rarity yang kena.
  -- Fallback ke rarity di bawahnya kalau ternyata pool-nya kosong
  -- (misal Mythic belum ke-seed lengkap).
  select * into v_character
  from public.characters
  where rarity = v_rarity
  order by random()
  limit 1;

  if v_character is null then
    select * into v_character
    from public.characters
    where rarity = 'Common'
    order by random()
    limit 1;
    v_rarity := 'Common';
  end if;

  if v_character is null then
    raise exception 'Belum ada karakter di database, seeding dulu';
  end if;

  -- Simpan/upsert ke koleksi user
  insert into public.user_characters (user_id, character_mal_id, count, last_obtained_at)
  values (v_user_id, v_character.mal_id, 1, now())
  on conflict (user_id, character_mal_id)
  do update set
    count = public.user_characters.count + 1,
    last_obtained_at = now()
  returning count into v_existing_count;

  return jsonb_build_object(
    'mal_id', v_character.mal_id,
    'name', v_character.name,
    'image_url', v_character.image_url,
    'anime_title', v_character.anime_title,
    'rarity', v_rarity,
    'is_new', v_existing_count = 1,
    'total_owned', v_existing_count,
    'remaining_balance', v_balance - p_cost
  );
end;
$$;

-- Cuma user yang login yang boleh manggil function ini
revoke all on function public.gacha_roll(integer) from public;
grant execute on function public.gacha_roll(integer) to authenticated;
