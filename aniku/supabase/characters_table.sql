-- Jalankan ini sekali di Supabase SQL Editor sebelum menjalankan workflow seeding.
-- Tabel referensi karakter anime buat sistem gacha (bukan tabel milik user,
-- jadi aman - gak nyentuh data user/donasi/clan yang udah ada).

create table if not exists public.characters (
  id bigserial primary key,
  mal_id integer unique not null,      -- ID karakter dari MyAnimeList, dipakai sebagai key upsert
  name text not null,
  image_url text,
  anime_mal_id integer,
  anime_title text,
  role text,                            -- "Main" atau "Supporting"
  rarity text not null,                 -- Common / Rare / Epic / Legendary / Mythic
  created_at timestamptz default now()
);

create index if not exists idx_characters_rarity on public.characters (rarity);
create index if not exists idx_characters_anime on public.characters (anime_mal_id);

-- Kalau mau reset total dan seed ulang dari awal:
-- truncate table public.characters;
