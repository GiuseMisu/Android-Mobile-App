# Seed assets (optional)

`seed_demo_data.py` works **out of the box** using public placeholder URLs
(pravatar.cc for avatars, loremflickr for photos). Drop your own files here to upload
them to Firebase Storage and use them instead — recommended for a polished demo.

```
seed_assets/
  avatars/   *.jpg|jpeg|png      → user profile pictures
  photos/    *.jpg|jpeg|png      → hike photos, review photos, cover photos
  audio/     *.mp3|m4a|ogg|wav   → in-hike voice notes (REQUIRED for working BirdNet)
```

Notes
- Uploading local files requires the `--bucket` flag (e.g. `wildtrail-dev-4de14.firebasestorage.app`,
  the `storage_bucket` from your `google-services.json`).
- **Bird audio:** the "Detect Bird" button only finds birds in *real* recordings. Grab a few
  Creative-Commons clips — e.g. from <https://xeno-canto.org> search `Turdus merula`
  (blackbird), `Erithacus rubecula` (robin), `Parus major` (great tit) — and drop the files in
  `audio/`. Without any audio files, hikes are seeded with photos only.
- Files are uploaded under the `seed/` prefix in your bucket, so `--wipe` can clean them up.
