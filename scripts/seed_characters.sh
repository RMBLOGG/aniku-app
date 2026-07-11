#!/usr/bin/env bash
# Seeding karakter anime dari Jikan API ke tabel `characters` di Supabase.
# Cuma nulis ke tabel referensi `characters` — gak nyentuh tabel user/donasi/clan.
# Pake upsert (on_conflict=mal_id), jadi aman dijalankan berkali-kali tanpa data dobel.
set -euo pipefail

SUPABASE_URL="${SUPABASE_URL:?Missing SUPABASE_URL}"
SUPABASE_KEY="${SUPABASE_SERVICE_KEY:?Missing SUPABASE_SERVICE_KEY}"
TOP_ANIME_COUNT="${TOP_ANIME_COUNT:-300}"
JIKAN_BASE="https://api.jikan.moe/v4"
DELAY=1.3   # detik, jaga2 di bawah limit 60 req/menit Jikan

PAGES=$(( (TOP_ANIME_COUNT + 24) / 25 ))
echo "Target: $TOP_ANIME_COUNT anime teratas (~$PAGES halaman /top/anime)"

rank_counter=0

for page in $(seq 1 "$PAGES"); do
  resp=$(curl -sf "$JIKAN_BASE/top/anime?page=${page}&limit=25") || { echo "Gagal fetch top/anime hal $page, skip"; sleep "$DELAY"; continue; }
  sleep "$DELAY"

  count=$(echo "$resp" | jq '.data | length')
  [ "$count" -eq 0 ] && break

  for i in $(seq 0 $((count - 1))); do
    rank_counter=$((rank_counter + 1))
    if [ "$rank_counter" -gt "$TOP_ANIME_COUNT" ]; then
      break 2
    fi

    anime_id=$(echo "$resp" | jq -r ".data[$i].mal_id")
    anime_title=$(echo "$resp" | jq -r ".data[$i].title")
    echo "[$rank_counter/$TOP_ANIME_COUNT] $anime_title (mal_id=$anime_id)"

    chars_resp=$(curl -sf "$JIKAN_BASE/anime/$anime_id/characters") || { echo "  gagal fetch characters, skip"; sleep "$DELAY"; continue; }
    sleep "$DELAY"

    # Hitung rarity dari kombinasi ranking anime + role karakter (Main/Supporting)
    payload=$(echo "$chars_resp" | jq --argjson rank "$rank_counter" --argjson anime_id "$anime_id" --arg anime_title "$anime_title" '
      [.data[]? | select(.character.mal_id != null) | {
        mal_id: .character.mal_id,
        name: .character.name,
        image_url: (.character.images.jpg.image_url // null),
        anime_mal_id: $anime_id,
        anime_title: $anime_title,
        role: .role,
        rarity: (
          if .role == "Main" then
            (if $rank <= 10 then "Mythic"
             elif $rank <= 30 then "Legendary"
             elif $rank <= 80 then "Epic"
             else "Rare" end)
          else
            (if $rank <= 30 then "Epic"
             elif $rank <= 100 then "Rare"
             else "Common" end)
          end
        )
      }]
    ')

    charcount=$(echo "$payload" | jq 'length')
    if [ "$charcount" -eq 0 ]; then
      echo "  (gak ada karakter, skip)"
      continue
    fi

    http_code=$(curl -s -o /tmp/supabase_resp.json -w "%{http_code}" \
      -X POST "$SUPABASE_URL/rest/v1/characters?on_conflict=mal_id" \
      -H "apikey: $SUPABASE_KEY" \
      -H "Authorization: Bearer $SUPABASE_KEY" \
      -H "Content-Type: application/json" \
      -H "Prefer: resolution=merge-duplicates" \
      -d "$payload")

    if [ "$http_code" -ge 300 ]; then
      echo "  WARNING upsert gagal (HTTP $http_code): $(cat /tmp/supabase_resp.json)"
    else
      echo "  -> $charcount karakter di-upsert"
    fi
  done
done

echo "Selesai. Total anime diproses: $rank_counter"
