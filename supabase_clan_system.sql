-- ============================================================
-- ANIKU: Sistem Diamond (DM) & Clan
-- Jalankan file ini di Supabase Dashboard > SQL Editor > New query
-- ============================================================

-- 1. Tambah kolom saldo Diamond ke profiles
alter table profiles add column if not exists diamond_balance integer not null default 0;

-- 2. Tabel Clan
create table if not exists clans (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  tag text not null unique,
  level integer not null default 1,
  total_xp integer not null default 0,
  leader_id uuid not null references profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);

-- 3. Tabel Member Clan (1 user cuma boleh 1 clan aktif, makanya user_id unique)
create table if not exists clan_members (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references clans(id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade unique,
  role text not null default 'member', -- 'leader' atau 'member'
  contributed_xp integer not null default 0,
  joined_at timestamptz not null default now()
);

-- 4. RLS: siapa aja boleh baca clan & member (buat leaderboard/directory publik)
alter table clans enable row level security;
alter table clan_members enable row level security;

drop policy if exists "clans_select_all" on clans;
create policy "clans_select_all" on clans for select using (true);

drop policy if exists "clan_members_select_all" on clan_members;
create policy "clan_members_select_all" on clan_members for select using (true);

-- Insert/update/delete langsung ke tabel clans & clan_members DILARANG dari client.
-- Semua write HARUS lewat RPC function di bawah (biar atomic & gak bisa dicurangin saldo DM-nya).

-- 5. RPC: Buat clan baru (potong 2000 DM dari pembuat, otomatis jadi leader)
create or replace function create_clan(p_name text, p_tag text, p_cost integer default 2000)
returns clans
language plpgsql
security definer
as $$
declare
  v_balance integer;
  v_clan clans;
begin
  if exists (select 1 from clan_members where user_id = auth.uid()) then
    raise exception 'Kamu sudah tergabung di sebuah clan';
  end if;

  select diamond_balance into v_balance from profiles where id = auth.uid();
  if v_balance is null or v_balance < p_cost then
    raise exception 'Saldo Diamond tidak cukup';
  end if;

  update profiles set diamond_balance = diamond_balance - p_cost where id = auth.uid();

  insert into clans (name, tag, leader_id) values (p_name, upper(p_tag), auth.uid())
  returning * into v_clan;

  insert into clan_members (clan_id, user_id, role) values (v_clan.id, auth.uid(), 'leader');

  return v_clan;
end;
$$;

-- 6. RPC: Gabung clan yang sudah ada (gratis)
create or replace function join_clan(p_clan_id uuid)
returns clan_members
language plpgsql
security definer
as $$
declare
  v_member clan_members;
begin
  if exists (select 1 from clan_members where user_id = auth.uid()) then
    raise exception 'Kamu sudah tergabung di sebuah clan';
  end if;

  insert into clan_members (clan_id, user_id, role) values (p_clan_id, auth.uid(), 'member')
  returning * into v_member;

  return v_member;
end;
$$;

-- 7. RPC: Keluar dari clan (leader gak bisa keluar sebelum transfer kepemimpinan / bubarin clan manual by admin)
create or replace function leave_clan()
returns void
language plpgsql
security definer
as $$
begin
  if exists (select 1 from clan_members where user_id = auth.uid() and role = 'leader') then
    raise exception 'Leader tidak bisa keluar, hubungi admin untuk membubarkan clan';
  end if;
  delete from clan_members where user_id = auth.uid();
end;
$$;

-- 8. RPC: Kontribusi DM ke clan sendiri (nambah XP clan + XP pribadi di clan itu)
create or replace function contribute_to_clan(p_amount integer)
returns void
language plpgsql
security definer
as $$
declare
  v_balance integer;
  v_clan_id uuid;
  v_new_total_xp integer;
begin
  if p_amount <= 0 then
    raise exception 'Jumlah kontribusi tidak valid';
  end if;

  select clan_id into v_clan_id from clan_members where user_id = auth.uid();
  if v_clan_id is null then
    raise exception 'Kamu belum tergabung di clan manapun';
  end if;

  select diamond_balance into v_balance from profiles where id = auth.uid();
  if v_balance is null or v_balance < p_amount then
    raise exception 'Saldo Diamond tidak cukup';
  end if;

  update profiles set diamond_balance = diamond_balance - p_amount where id = auth.uid();
  update clan_members set contributed_xp = contributed_xp + p_amount where user_id = auth.uid();

  update clans set total_xp = total_xp + p_amount where id = v_clan_id
    returning total_xp into v_new_total_xp;

  -- Level clan: setiap kelipatan 1000 XP naik 1 level (silakan sesuaikan formula)
  update clans set level = greatest(1, (v_new_total_xp / 1000) + 1) where id = v_clan_id;
end;
$$;

-- 9. RPC: Admin nambah saldo DM user (dipanggil dari Admin Panel, untuk top-up manual)
create or replace function admin_add_diamond(p_user_id uuid, p_amount integer)
returns void
language plpgsql
security definer
as $$
declare
  v_caller_role text;
begin
  select role into v_caller_role from profiles where id = auth.uid();
  if v_caller_role is distinct from 'admin' then
    raise exception 'Hanya admin yang boleh menambah saldo Diamond';
  end if;
  update profiles set diamond_balance = diamond_balance + p_amount where id = p_user_id;
end;
$$;

grant execute on function create_clan(text, text, integer) to authenticated;
grant execute on function join_clan(uuid) to authenticated;
grant execute on function leave_clan() to authenticated;
grant execute on function contribute_to_clan(integer) to authenticated;
grant execute on function admin_add_diamond(uuid, integer) to authenticated;

-- ============================================================
-- UPDATE: Custom Clan Icon (upload dari galeri, cuma leader yang bisa ubah)
-- Jalankan tambahan ini kalau tabel clans sudah dibuat sebelumnya
-- ============================================================
alter table clans add column if not exists icon_url text;

create or replace function update_clan_icon(p_clan_id uuid, p_icon_url text)
returns void
language plpgsql
security definer
as $$
begin
  if not exists (
    select 1 from clan_members
    where clan_id = p_clan_id and user_id = auth.uid() and role = 'leader'
  ) then
    raise exception 'Hanya leader yang boleh mengubah icon clan';
  end if;

  update clans set icon_url = p_icon_url where id = p_clan_id;
end;
$$;

grant execute on function update_clan_icon(uuid, text) to authenticated;

-- ============================================================
-- UPDATE: Manajemen Clan (kick, hapus, rename berbayar, privasi + approval join)
-- ============================================================
alter table clans add column if not exists is_private boolean not null default false;

create table if not exists clan_join_requests (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references clans(id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  status text not null default 'pending', -- 'pending' | 'approved' | 'rejected'
  requested_at timestamptz not null default now(),
  unique(clan_id, user_id)
);

alter table clan_join_requests enable row level security;

drop policy if exists "clan_join_requests_select" on clan_join_requests;
create policy "clan_join_requests_select" on clan_join_requests for select using (
  user_id = auth.uid()
  or exists (select 1 from clans where id = clan_id and leader_id = auth.uid())
);

-- Ganti join_clan: kalau clan private, gak langsung masuk, harus lewat request
create or replace function join_clan(p_clan_id uuid)
returns clan_members
language plpgsql
security definer
as $$
declare
  v_member clan_members;
  v_is_private boolean;
begin
  if exists (select 1 from clan_members where user_id = auth.uid()) then
    raise exception 'Kamu sudah tergabung di sebuah clan';
  end if;

  select is_private into v_is_private from clans where id = p_clan_id;
  if v_is_private then
    raise exception 'Clan ini private, gunakan request_join_clan untuk minta persetujuan';
  end if;

  insert into clan_members (clan_id, user_id, role) values (p_clan_id, auth.uid(), 'member')
  returning * into v_member;

  return v_member;
end;
$$;

-- Request gabung clan private
create or replace function request_join_clan(p_clan_id uuid)
returns clan_join_requests
language plpgsql
security definer
as $$
declare
  v_request clan_join_requests;
begin
  if exists (select 1 from clan_members where user_id = auth.uid()) then
    raise exception 'Kamu sudah tergabung di sebuah clan';
  end if;

  insert into clan_join_requests (clan_id, user_id, status) values (p_clan_id, auth.uid(), 'pending')
  on conflict (clan_id, user_id) do update set status = 'pending', requested_at = now()
  returning * into v_request;

  return v_request;
end;
$$;

-- Leader approve request
create or replace function approve_join_request(p_request_id uuid)
returns void
language plpgsql
security definer
as $$
declare
  v_req clan_join_requests;
begin
  select * into v_req from clan_join_requests where id = p_request_id;
  if v_req is null then
    raise exception 'Request tidak ditemukan';
  end if;

  if not exists (select 1 from clans where id = v_req.clan_id and leader_id = auth.uid()) then
    raise exception 'Hanya leader yang boleh approve';
  end if;

  if exists (select 1 from clan_members where user_id = v_req.user_id) then
    update clan_join_requests set status = 'rejected' where id = p_request_id;
    raise exception 'User sudah tergabung di clan lain';
  end if;

  insert into clan_members (clan_id, user_id, role) values (v_req.clan_id, v_req.user_id, 'member');
  update clan_join_requests set status = 'approved' where id = p_request_id;
end;
$$;

-- Leader reject request
create or replace function reject_join_request(p_request_id uuid)
returns void
language plpgsql
security definer
as $$
begin
  if not exists (
    select 1 from clan_join_requests r
    join clans c on c.id = r.clan_id
    where r.id = p_request_id and c.leader_id = auth.uid()
  ) then
    raise exception 'Hanya leader yang boleh reject';
  end if;
  update clan_join_requests set status = 'rejected' where id = p_request_id;
end;
$$;

-- Leader kick member
create or replace function kick_member(p_clan_id uuid, p_user_id uuid)
returns void
language plpgsql
security definer
as $$
begin
  if not exists (select 1 from clans where id = p_clan_id and leader_id = auth.uid()) then
    raise exception 'Hanya leader yang boleh mengeluarkan member';
  end if;
  if p_user_id = auth.uid() then
    raise exception 'Leader tidak bisa kick diri sendiri';
  end if;
  delete from clan_members where clan_id = p_clan_id and user_id = p_user_id;
end;
$$;

-- Leader hapus clan (semua member ikut kehapus lewat cascade)
create or replace function delete_clan(p_clan_id uuid)
returns void
language plpgsql
security definer
as $$
begin
  if not exists (select 1 from clans where id = p_clan_id and leader_id = auth.uid()) then
    raise exception 'Hanya leader yang boleh menghapus clan';
  end if;
  delete from clans where id = p_clan_id;
end;
$$;

-- Leader ganti nama clan, biaya 1.000 DM dipotong dari saldo leader
create or replace function rename_clan(p_clan_id uuid, p_new_name text, p_cost integer default 1000)
returns void
language plpgsql
security definer
as $$
declare
  v_balance integer;
begin
  if not exists (select 1 from clans where id = p_clan_id and leader_id = auth.uid()) then
    raise exception 'Hanya leader yang boleh ganti nama clan';
  end if;

  select diamond_balance into v_balance from profiles where id = auth.uid();
  if v_balance is null or v_balance < p_cost then
    raise exception 'Saldo Diamond tidak cukup';
  end if;

  update profiles set diamond_balance = diamond_balance - p_cost where id = auth.uid();
  update clans set name = p_new_name where id = p_clan_id;
end;
$$;

-- Leader ubah privasi clan (gratis)
create or replace function set_clan_privacy(p_clan_id uuid, p_is_private boolean)
returns void
language plpgsql
security definer
as $$
begin
  if not exists (select 1 from clans where id = p_clan_id and leader_id = auth.uid()) then
    raise exception 'Hanya leader yang boleh ubah privasi clan';
  end if;
  update clans set is_private = p_is_private where id = p_clan_id;
end;
$$;

grant execute on function request_join_clan(uuid) to authenticated;
grant execute on function approve_join_request(uuid) to authenticated;
grant execute on function reject_join_request(uuid) to authenticated;
grant execute on function kick_member(uuid, uuid) to authenticated;
grant execute on function delete_clan(uuid) to authenticated;
grant execute on function rename_clan(uuid, text, integer) to authenticated;
grant execute on function set_clan_privacy(uuid, boolean) to authenticated;
