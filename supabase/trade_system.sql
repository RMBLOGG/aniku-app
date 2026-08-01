-- =========================================================
-- FITUR: Trade / Jual-Beli Kartu Gacha antar user (bayar pake DM)
-- =========================================================
-- Sama kayak gacha_system.sql: SEMUA penulisan (bikin listing, beli,
-- batalin) WAJIB lewat function SECURITY DEFINER di bawah. Gak ada
-- policy insert/update/delete langsung buat role authenticated di
-- trade_listings maupun user_characters, biar gak bisa dicurangin
-- dari client (misal ubah harga orang lain, atau transfer kartu tanpa
-- bayar).
--
-- ⚠️ SESUAIKAN DULU sebelum dijalankan (sama kayak gacha_system.sql):
-- pastiin nama tabel "public.profiles" dan kolom "diamond_balance"
-- di bawah persis sama kayak yang beneran ada di project Aniku kamu.

-- =========================================================
-- BAGIAN 1: Tabel trade_listings
-- =========================================================
create table if not exists public.trade_listings (
  id bigserial primary key,
  seller_id uuid not null references auth.users(id) on delete cascade,
  character_mal_id integer not null references public.characters(mal_id) on delete cascade,
  price_dm integer not null check (price_dm >= 10),
  status text not null default 'active' check (status in ('active', 'sold', 'cancelled')),
  buyer_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  sold_at timestamptz
);

create index if not exists idx_trade_listings_active
  on public.trade_listings (status, created_at desc)
  where status = 'active';

create index if not exists idx_trade_listings_seller
  on public.trade_listings (seller_id, status);

alter table public.trade_listings enable row level security;

-- Semua orang yang login boleh liat listing yang masih aktif (buat browsing
-- pasar), ATAU listing punya diri sendiri (biar bisa liat history sold/cancelled-nya).
create policy "Liat listing aktif atau milik sendiri"
on public.trade_listings
for select
to authenticated
using (status = 'active' or seller_id = auth.uid());

-- Gak ada policy insert/update/delete sama sekali -- semua lewat RPC di bawah.


-- =========================================================
-- BAGIAN 2: Konfigurasi anti-abuse
-- =========================================================
-- Fee marketplace: dipotong dari harga jual, TIDAK masuk ke seller.
-- Ini biar DM ada "sink" beneran (gak cuma pindah tangan doang / murni
-- currency-in currency-out yang rawan disalahgunain buat cuci saldo
-- antar akun sendiri).
-- 10% dibulatkan ke bawah, minimum potongan 1 DM.
create or replace function public.trade_calc_fee(p_price integer)
returns integer
language sql
immutable
as $$
  select greatest(1, floor(p_price * 0.10)::integer);
$$;

-- Maksimal listing aktif bersamaan per user (anti-spam pasar)
-- diset di dalam function create_trade_listing di bawah (MAX_ACTIVE_LISTINGS).


-- =========================================================
-- BAGIAN 3: Function create_trade_listing()
-- =========================================================
create or replace function public.create_trade_listing(
  p_character_mal_id integer,
  p_price_dm integer
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_owned_count integer;
  v_active_count integer;
  v_already_listed boolean;
  v_new_id bigint;
  MAX_ACTIVE_LISTINGS constant integer := 20;
begin
  if v_user_id is null then
    raise exception 'Harus login dulu buat jualan kartu';
  end if;

  if p_price_dm < 10 then
    raise exception 'Harga minimal 10 DM';
  end if;

  if p_price_dm > 1000000 then
    raise exception 'Harga kemahalan, wajar dikit dong';
  end if;

  -- Kunci baris koleksi user ini buat karakter ini, biar gak race
  -- condition kalau dia bikin 2 listing sekaligus dari 2 device.
  select count into v_owned_count
  from public.user_characters
  where user_id = v_user_id and character_mal_id = p_character_mal_id
  for update;

  if v_owned_count is null or v_owned_count < 1 then
    raise exception 'Kamu gak punya kartu ini';
  end if;

  -- Kartu yang lagi aktif dijual gak boleh dijual lagi bareng listing lain
  -- (satu listing = satu kartu dari koleksi user ini dikunci sampe
  -- terjual/dibatalin, mencegah user jual "melebihi" stok yang dia punya
  -- kalau dia punya count > 1 tapi cuma boleh 1 listing aktif per karakter).
  select exists(
    select 1 from public.trade_listings
    where seller_id = v_user_id
      and character_mal_id = p_character_mal_id
      and status = 'active'
  ) into v_already_listed;

  if v_already_listed then
    raise exception 'Kamu udah ada listing aktif buat karakter ini';
  end if;

  select count(*) into v_active_count
  from public.trade_listings
  where seller_id = v_user_id and status = 'active';

  if v_active_count >= MAX_ACTIVE_LISTINGS then
    raise exception 'Maksimal % listing aktif bersamaan', MAX_ACTIVE_LISTINGS;
  end if;

  insert into public.trade_listings (seller_id, character_mal_id, price_dm)
  values (v_user_id, p_character_mal_id, p_price_dm)
  returning id into v_new_id;

  return jsonb_build_object(
    'listing_id', v_new_id,
    'character_mal_id', p_character_mal_id,
    'price_dm', p_price_dm,
    'status', 'active'
  );
end;
$$;

revoke all on function public.create_trade_listing(integer, integer) from public;
grant execute on function public.create_trade_listing(integer, integer) to authenticated;


-- =========================================================
-- BAGIAN 4: Function cancel_trade_listing()
-- =========================================================
create or replace function public.cancel_trade_listing(p_listing_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_listing record;
begin
  if v_user_id is null then
    raise exception 'Harus login dulu';
  end if;

  select * into v_listing
  from public.trade_listings
  where id = p_listing_id
  for update;

  if v_listing is null then
    raise exception 'Listing gak ketemu';
  end if;

  if v_listing.seller_id != v_user_id then
    raise exception 'Bukan listing kamu';
  end if;

  if v_listing.status != 'active' then
    raise exception 'Listing ini udah gak aktif';
  end if;

  update public.trade_listings
  set status = 'cancelled'
  where id = p_listing_id;

  return jsonb_build_object('listing_id', p_listing_id, 'status', 'cancelled');
end;
$$;

revoke all on function public.cancel_trade_listing(bigint) from public;
grant execute on function public.cancel_trade_listing(bigint) to authenticated;


-- =========================================================
-- BAGIAN 5: Function buy_trade_listing()
-- =========================================================
create or replace function public.buy_trade_listing(p_listing_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_buyer_id uuid := auth.uid();
  v_listing record;
  v_buyer_balance integer;
  v_seller_owned_count integer;
  v_fee integer;
  v_seller_proceeds integer;
  v_character record;
begin
  if v_buyer_id is null then
    raise exception 'Harus login dulu buat beli';
  end if;

  -- Kunci baris listing dulu (urutan lock: listing -> buyer profile ->
  -- seller profile -> user_characters, konsisten di semua path biar gak
  -- deadlock kalau ada 2 transaksi jalan bersamaan).
  select * into v_listing
  from public.trade_listings
  where id = p_listing_id
  for update;

  if v_listing is null then
    raise exception 'Listing gak ketemu';
  end if;

  if v_listing.status != 'active' then
    raise exception 'Kartu ini udah keduluan dibeli/dibatalin';
  end if;

  if v_listing.seller_id = v_buyer_id then
    raise exception 'Gak bisa beli listing kamu sendiri';
  end if;

  -- Kunci saldo buyer
  select diamond_balance into v_buyer_balance
  from public.profiles
  where id = v_buyer_id
  for update;

  if v_buyer_balance is null then
    raise exception 'Profil kamu gak ketemu';
  end if;

  if v_buyer_balance < v_listing.price_dm then
    raise exception 'DM gak cukup. Saldo kamu %, butuh %', v_buyer_balance, v_listing.price_dm;
  end if;

  -- Kunci baris koleksi seller, pastiin dia masih beneran punya kartunya
  -- (jaga-jaga kalau ada cara lain kartu itu ilang setelah listing dibikin).
  select count into v_seller_owned_count
  from public.user_characters
  where user_id = v_listing.seller_id and character_mal_id = v_listing.character_mal_id
  for update;

  if v_seller_owned_count is null or v_seller_owned_count < 1 then
    update public.trade_listings set status = 'cancelled' where id = p_listing_id;
    raise exception 'Seller udah gak punya kartu ini, listing dibatalin otomatis';
  end if;

  v_fee := public.trade_calc_fee(v_listing.price_dm);
  v_seller_proceeds := v_listing.price_dm - v_fee;

  -- Potong DM buyer
  update public.profiles
  set diamond_balance = diamond_balance - v_listing.price_dm
  where id = v_buyer_id;

  -- Kredit DM seller (udah dipotong fee)
  update public.profiles
  set diamond_balance = diamond_balance + v_seller_proceeds
  where id = v_listing.seller_id;

  -- Pindahin 1 kartu: kurangin count seller, tambahin/upsert count buyer
  update public.user_characters
  set count = count - 1
  where user_id = v_listing.seller_id and character_mal_id = v_listing.character_mal_id;

  delete from public.user_characters
  where user_id = v_listing.seller_id
    and character_mal_id = v_listing.character_mal_id
    and count <= 0;

  insert into public.user_characters (user_id, character_mal_id, count, last_obtained_at)
  values (v_buyer_id, v_listing.character_mal_id, 1, now())
  on conflict (user_id, character_mal_id)
  do update set
    count = public.user_characters.count + 1,
    last_obtained_at = now();

  update public.trade_listings
  set status = 'sold', buyer_id = v_buyer_id, sold_at = now()
  where id = p_listing_id;

  select * into v_character from public.characters where mal_id = v_listing.character_mal_id;

  return jsonb_build_object(
    'listing_id', p_listing_id,
    'character_mal_id', v_listing.character_mal_id,
    'character_name', v_character.name,
    'price_dm', v_listing.price_dm,
    'fee_dm', v_fee,
    'remaining_balance', v_buyer_balance - v_listing.price_dm
  );
end;
$$;

revoke all on function public.buy_trade_listing(bigint) from public;
grant execute on function public.buy_trade_listing(bigint) to authenticated;


-- =========================================================
-- BAGIAN 6: View pasar (buat ditampilin di client, sudah join karakter + seller)
-- =========================================================
create or replace view public.trade_listings_market as
select
  tl.id as listing_id,
  tl.character_mal_id,
  tl.price_dm,
  tl.created_at,
  tl.seller_id,
  c.name as character_name,
  c.image_url as character_image_url,
  c.anime_title,
  c.rarity,
  p.username as seller_username,
  p.avatar_url as seller_avatar_url
from public.trade_listings tl
join public.characters c on c.mal_id = tl.character_mal_id
join public.profiles p on p.id = tl.seller_id
where tl.status = 'active'
order by tl.created_at desc;

grant select on public.trade_listings_market to authenticated;
