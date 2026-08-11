-- =========================================================
-- BADGE STORE — beli & pakai TAG CLAN ASLI (dari tabel public.clans)
-- dengan BEBERAPA PILIHAN DESAIN (skin) per clan, pakai Diamond.
-- Cuma anggota clan yang bersangkutan yang boleh beli badge tag
-- clan-nya sendiri (dicek dari tabel clan_members).
--
-- Tiap clan otomatis punya 6 varian skin buat dipilih:
--   ribbon_standard, ribbon_holo, ribbon_neon,
--   pennant_standard, pennant_holo, pennant_neon
-- Harga tiap skin = badge_price_diamond clan x pengali skin-nya.
--
-- Jalankan ini sekali di Supabase SQL Editor.
-- ⚠️ Asumsi tabel saldo Diamond ada di "public.profiles" kolom
-- "diamond_balance", dan tabel clan asli "public.clans" punya kolom
-- id (uuid), tag (text), name (text), icon_url (text).
-- Sesuaikan kalau beda nama di project kamu.
-- =========================================================

-- =========================================================
-- BAGIAN 1: Kolom tambahan di tabel clans (harga dasar & warna badge)
-- =========================================================
alter table public.clans
  add column if not exists badge_color text,              -- hex, kosongin = auto pilih warna
  add column if not exists badge_price_diamond integer not null default 500;  -- harga dasar (skin standard)


-- =========================================================
-- BAGIAN 2: Warna default kalau clan belum di-set badge_color manual
-- =========================================================
create or replace function public.clan_default_badge_color(p_tag text)
returns text
language sql
immutable
as $$
  select (array['#8A4FD6','#2FA8BF','#F5A623','#E0473F','#4FD1C5','#D4AF37','#3B82F6','#EC4899'])
    [(abs(hashtext(p_tag)) % 8) + 1];
$$;


-- =========================================================
-- BAGIAN 3: Katalog SKIN/desain badge (fixed, gak per-clan) - ini yang
-- bikin ada "beberapa pilihan desain" buat clan yang sama.
-- =========================================================
create table if not exists public.badge_skins (
  id text primary key,          -- 'ribbon_standard', 'pennant_neon', dst
  shape text not null,          -- 'ribbon' atau 'pennant'
  tier text not null,           -- 'standard', 'holo', atau 'neon'
  display_name text not null,
  price_multiplier numeric not null default 1,
  sort_order integer not null default 0
);

insert into public.badge_skins (id, shape, tier, display_name, price_multiplier, sort_order) values
  ('ribbon_standard', 'ribbon', 'standard', 'Ribbon Standard', 1, 1),
  ('ribbon_holo', 'ribbon', 'holo', 'Ribbon Holo', 2.5, 2),
  ('ribbon_neon', 'ribbon', 'neon', 'Ribbon Neon', 5, 3),
  ('pennant_standard', 'pennant', 'standard', 'Pennant Standard', 1.2, 4),
  ('pennant_holo', 'pennant', 'holo', 'Pennant Holo', 3, 5),
  ('pennant_neon', 'pennant', 'neon', 'Pennant Neon', 6, 6)
on conflict (id) do nothing;

alter table public.badge_skins enable row level security;
drop policy if exists "Semua orang login boleh liat katalog skin" on public.badge_skins;
create policy "Semua orang login boleh liat katalog skin"
on public.badge_skins
for select
to authenticated
using (true);


-- =========================================================
-- BAGIAN 4: View katalog badge = clan yg diikutin user × skin yg ada
-- (jadi 1 clan bisa muncul beberapa kartu, 1 per skin)
-- =========================================================
drop view if exists public.clan_badge_catalog;
create view public.clan_badge_catalog as
select
  c.id as clan_id,
  c.tag,
  c.name,
  c.icon_url,
  coalesce(c.badge_color, public.clan_default_badge_color(c.tag)) as badge_color,
  s.id as skin_id,
  s.shape as badge_shape,
  s.tier as badge_tier,
  s.display_name as skin_name,
  round(c.badge_price_diamond * s.price_multiplier)::integer as badge_price_diamond
from public.clans c
cross join public.badge_skins s
where exists (
    select 1 from public.clan_members cm
    where cm.clan_id = c.id and cm.user_id = auth.uid()
  )
order by s.sort_order;

grant select on public.clan_badge_catalog to authenticated;


-- =========================================================
-- BAGIAN 5: Kepemilikan badge - sekarang per (user, clan, skin)
-- =========================================================
create table if not exists public.user_owned_clan_badges (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  clan_id uuid not null references public.clans(id) on delete cascade,
  skin_id text not null references public.badge_skins(id) on delete cascade default 'ribbon_standard',
  purchased_at timestamptz default now()
);

-- Kalau kolom skin_id belum ada (upgrade dari versi lama), tambahin manual:
alter table public.user_owned_clan_badges
  add column if not exists skin_id text not null default 'ribbon_standard' references public.badge_skins(id);

drop index if exists idx_user_owned_clan_badges_unique;
create unique index if not exists idx_user_owned_clan_badges_unique_v2
  on public.user_owned_clan_badges (user_id, clan_id, skin_id);

create index if not exists idx_user_owned_clan_badges_user on public.user_owned_clan_badges (user_id);

alter table public.user_owned_clan_badges enable row level security;

drop policy if exists "User baca badge clan miliknya sendiri" on public.user_owned_clan_badges;
create policy "User baca badge clan miliknya sendiri"
on public.user_owned_clan_badges
for select
to authenticated
using (auth.uid() = user_id);


-- =========================================================
-- BAGIAN 6: Badge clan + skin yg lagi dipakai (equipped) di profil
-- =========================================================
alter table public.profiles
  add column if not exists equipped_clan_badge_id uuid references public.clans(id) on delete set null,
  add column if not exists equipped_badge_skin_id text references public.badge_skins(id) on delete set null;


-- =========================================================
-- BAGIAN 7: View publik badge clan+skin yg lagi dipakai tiap user
-- =========================================================
drop view if exists public.equipped_badges_public;
create view public.equipped_badges_public as
select
  p.id as user_id,
  c.id as clan_id,
  c.tag as label,
  coalesce(c.badge_color, public.clan_default_badge_color(c.tag)) as background_color,
  '#FFFFFF'::text as text_color,
  s.id as skin_id,
  s.shape as badge_shape,
  s.tier as badge_tier
from public.profiles p
join public.clans c on c.id = p.equipped_clan_badge_id
join public.badge_skins s on s.id = p.equipped_badge_skin_id;

grant select on public.equipped_badges_public to authenticated;


-- =========================================================
-- BAGIAN 8: Function buy_clan_badge() — beli 1 varian skin dari clan
-- =========================================================
create or replace function public.buy_clan_badge(p_clan_id uuid, p_skin_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_balance integer;
  v_clan record;
  v_skin record;
  v_price integer;
  v_already_owned boolean;
  v_is_member boolean;
begin
  if v_user_id is null then
    raise exception 'Harus login dulu buat beli badge';
  end if;

  select id, tag, badge_price_diamond into v_clan
  from public.clans
  where id = p_clan_id;

  if v_clan is null then
    raise exception 'Clan gak ketemu';
  end if;

  select id, price_multiplier into v_skin
  from public.badge_skins
  where id = p_skin_id;

  if v_skin is null then
    raise exception 'Desain badge gak ketemu';
  end if;

  select exists(
    select 1 from public.clan_members
    where user_id = v_user_id and clan_id = p_clan_id
  ) into v_is_member;

  if not v_is_member then
    raise exception 'Cuma anggota clan ini yang boleh beli badge tag-nya';
  end if;

  select exists(
    select 1 from public.user_owned_clan_badges
    where user_id = v_user_id and clan_id = p_clan_id and skin_id = p_skin_id
  ) into v_already_owned;

  if v_already_owned then
    raise exception 'Desain badge ini udah kamu punya';
  end if;

  v_price := round(v_clan.badge_price_diamond * v_skin.price_multiplier);

  -- Cek & kunci baris saldo user ini biar gak race condition kalau spam klik beli
  select diamond_balance into v_balance
  from public.profiles
  where id = v_user_id
  for update;

  if v_balance is null then
    raise exception 'Profil user gak ketemu';
  end if;

  if v_balance < v_price then
    raise exception 'DM gak cukup. Saldo kamu %, butuh %', v_balance, v_price;
  end if;

  update public.profiles
  set diamond_balance = diamond_balance - v_price
  where id = v_user_id;

  insert into public.user_owned_clan_badges (user_id, clan_id, skin_id)
  values (v_user_id, p_clan_id, p_skin_id);

  return jsonb_build_object(
    'clan_id', v_clan.id,
    'tag', v_clan.tag,
    'skin_id', p_skin_id,
    'price_diamond', v_price,
    'remaining_balance', v_balance - v_price
  );
end;
$$;

revoke all on function public.buy_clan_badge(uuid, text) from public;
grant execute on function public.buy_clan_badge(uuid, text) to authenticated;


-- =========================================================
-- BAGIAN 9: Function equip_clan_badge() — pakai/lepas 1 varian skin
-- p_clan_id / p_skin_id = NULL artinya lepas badge (gak pakai apapun)
-- =========================================================
create or replace function public.equip_clan_badge(p_clan_id uuid, p_skin_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_owned boolean;
begin
  if v_user_id is null then
    raise exception 'Harus login dulu';
  end if;

  if p_clan_id is not null and p_skin_id is not null then
    select exists(
      select 1 from public.user_owned_clan_badges
      where user_id = v_user_id and clan_id = p_clan_id and skin_id = p_skin_id
    ) into v_owned;

    if not v_owned then
      raise exception 'Kamu belum punya desain badge ini';
    end if;
  end if;

  update public.profiles
  set equipped_clan_badge_id = p_clan_id,
      equipped_badge_skin_id = p_skin_id
  where id = v_user_id;

  return jsonb_build_object('equipped_clan_badge_id', p_clan_id, 'equipped_badge_skin_id', p_skin_id);
end;
$$;

revoke all on function public.equip_clan_badge(uuid, text) from public;
grant execute on function public.equip_clan_badge(uuid, text) to authenticated;


-- =========================================================
-- BAGIAN 10: function lama buy_clan_badge(uuid)/equip_clan_badge(uuid)
-- (versi 1 parameter, sebelum ada pilihan skin) udah gak kepake,
-- aman dihapus biar gak nyangkut 2 versi function berbeda.
-- =========================================================
drop function if exists public.buy_clan_badge(uuid);
drop function if exists public.equip_clan_badge(uuid);
