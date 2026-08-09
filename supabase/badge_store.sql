-- =========================================================
-- BADGE STORE — beli & pakai TAG CLAN ASLI (dari tabel public.clans)
-- pakai Diamond. Cuma anggota clan yang bersangkutan yang boleh beli
-- badge tag clan-nya sendiri (dicek dari tabel clan_members) -- clan
-- private/publik gak masalah, yang penting emang anggotanya.
--
-- Jalankan ini sekali di Supabase SQL Editor.
-- ⚠️ Asumsi tabel saldo Diamond ada di "public.profiles" kolom
-- "diamond_balance", dan tabel clan asli "public.clans" punya kolom
-- id (uuid), tag (text), name (text), icon_url (text), is_private (bool).
-- Sesuaikan kalau beda nama di project kamu.
--
-- Bentuk badge (badge_shape) bisa diedit manual per-clan di Table Editor:
-- 'ribbon' (kayak "JF"/"TSR" - sudut kanan-bawah dipotong) atau
-- 'pennant' (kayak "OTF" - ujung kanan runcing ke tengah).
-- =========================================================

-- =========================================================
-- BAGIAN 1: Kolom tambahan di tabel clans buat kebutuhan badge store
-- (harga & warna badge, bisa diedit manual per-clan lewat Table Editor)
-- =========================================================
alter table public.clans
  add column if not exists badge_color text,              -- hex, kosongin = auto pilih warna
  add column if not exists badge_shape text not null default 'ribbon',  -- 'ribbon' atau 'pennant'
  add column if not exists badge_price_diamond integer not null default 500;


-- =========================================================
-- BAGIAN 2: Warna default kalau clan belum di-set badge_color manual
-- (dipilih otomatis & konsisten berdasarkan tag clan-nya)
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
-- BAGIAN 3: View katalog badge (cuma clan yg USER SEDANG JADI ANGGOTA
-- yg muncul, biar gak bisa beli tag clan orang laen)
-- =========================================================
drop view if exists public.clan_badge_catalog;
create view public.clan_badge_catalog as
select
  c.id as clan_id,
  c.tag,
  c.name,
  c.icon_url,
  coalesce(c.badge_color, public.clan_default_badge_color(c.tag)) as badge_color,
  c.badge_shape,
  c.badge_price_diamond
from public.clans c
where exists (
    select 1 from public.clan_members cm
    where cm.clan_id = c.id and cm.user_id = auth.uid()
  );

grant select on public.clan_badge_catalog to authenticated;


-- =========================================================
-- BAGIAN 4: Kepemilikan badge tag-clan tiap user
-- =========================================================
create table if not exists public.user_owned_clan_badges (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  clan_id uuid not null references public.clans(id) on delete cascade,
  purchased_at timestamptz default now()
);

create unique index if not exists idx_user_owned_clan_badges_unique
  on public.user_owned_clan_badges (user_id, clan_id);

create index if not exists idx_user_owned_clan_badges_user on public.user_owned_clan_badges (user_id);

alter table public.user_owned_clan_badges enable row level security;

-- User cuma boleh baca badge miliknya sendiri. Gak ada policy insert/update/delete
-- buat client -- semua penulisan WAJIB lewat function buy_clan_badge() di bawah.
drop policy if exists "User baca badge clan miliknya sendiri" on public.user_owned_clan_badges;
create policy "User baca badge clan miliknya sendiri"
on public.user_owned_clan_badges
for select
to authenticated
using (auth.uid() = user_id);


-- =========================================================
-- BAGIAN 5: Kolom badge clan yg lagi dipakai (equipped) di profil
-- Sengaja terpisah dari status clan asli (misal kolom clan_id di
-- clan_members) -- ini murni kosmetik, gak ngerubah keanggotaan clan.
-- =========================================================
alter table public.profiles
  add column if not exists equipped_clan_badge_id uuid references public.clans(id) on delete set null;


-- =========================================================
-- BAGIAN 6: View publik badge clan yg lagi dipakai tiap user
-- (dipakai buat nampilin badge di chat, mirip clanTagMap yg udah ada,
-- tapi ini badge KOSMETIK, bisa beda dari clan asli user tsb)
-- =========================================================
drop view if exists public.equipped_badges_public;
create view public.equipped_badges_public as
select
  p.id as user_id,
  c.id as clan_id,
  c.tag as label,
  coalesce(c.badge_color, public.clan_default_badge_color(c.tag)) as background_color,
  '#FFFFFF'::text as text_color,
  c.badge_shape,
  c.badge_price_diamond as price_diamond
from public.profiles p
join public.clans c on c.id = p.equipped_clan_badge_id;

grant select on public.equipped_badges_public to authenticated;


-- =========================================================
-- BAGIAN 7: Function buy_clan_badge() — beli tag clan, potong Diamond
-- Cuma anggota clan tsb yang boleh beli badge tag clan-nya sendiri.
-- =========================================================
create or replace function public.buy_clan_badge(p_clan_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_balance integer;
  v_clan record;
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

  select exists(
    select 1 from public.clan_members
    where user_id = v_user_id and clan_id = p_clan_id
  ) into v_is_member;

  if not v_is_member then
    raise exception 'Cuma anggota clan ini yang boleh beli badge tag-nya';
  end if;

  select exists(
    select 1 from public.user_owned_clan_badges
    where user_id = v_user_id and clan_id = p_clan_id
  ) into v_already_owned;

  if v_already_owned then
    raise exception 'Badge tag clan ini udah kamu punya';
  end if;

  -- Cek & kunci baris saldo user ini biar gak race condition kalau spam klik beli
  select diamond_balance into v_balance
  from public.profiles
  where id = v_user_id
  for update;

  if v_balance is null then
    raise exception 'Profil user gak ketemu';
  end if;

  if v_balance < v_clan.badge_price_diamond then
    raise exception 'DM gak cukup. Saldo kamu %, butuh %', v_balance, v_clan.badge_price_diamond;
  end if;

  update public.profiles
  set diamond_balance = diamond_balance - v_clan.badge_price_diamond
  where id = v_user_id;

  insert into public.user_owned_clan_badges (user_id, clan_id)
  values (v_user_id, p_clan_id);

  return jsonb_build_object(
    'clan_id', v_clan.id,
    'tag', v_clan.tag,
    'price_diamond', v_clan.badge_price_diamond,
    'remaining_balance', v_balance - v_clan.badge_price_diamond
  );
end;
$$;

revoke all on function public.buy_clan_badge(uuid) from public;
grant execute on function public.buy_clan_badge(uuid) to authenticated;


-- =========================================================
-- BAGIAN 8: Function equip_clan_badge() — pakai/lepas badge tag clan
-- p_clan_id = NULL artinya lepas badge (gak pakai badge apapun)
-- =========================================================
create or replace function public.equip_clan_badge(p_clan_id uuid)
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

  if p_clan_id is not null then
    select exists(
      select 1 from public.user_owned_clan_badges
      where user_id = v_user_id and clan_id = p_clan_id
    ) into v_owned;

    if not v_owned then
      raise exception 'Kamu belum punya badge tag clan ini';
    end if;
  end if;

  update public.profiles
  set equipped_clan_badge_id = p_clan_id
  where id = v_user_id;

  return jsonb_build_object('equipped_clan_badge_id', p_clan_id);
end;
$$;

revoke all on function public.equip_clan_badge(uuid) from public;
grant execute on function public.equip_clan_badge(uuid) to authenticated;


-- =========================================================
-- BAGIAN 9 (opsional): kalau kamu udah sempat run versi lama
-- badge_store.sql (yg pakai badge_store_items generik), bersihin di sini.
-- Aman di-skip kalau belum pernah run versi lama.
-- =========================================================
-- alter table public.profiles drop column if exists equipped_badge_id;
-- drop table if exists public.user_owned_badges;
-- drop table if exists public.badge_store_items;
-- drop function if exists public.buy_badge(bigint);
-- drop function if exists public.equip_badge(bigint);
