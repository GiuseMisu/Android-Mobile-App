# WildTrail — demo data seeding

`seed_demo_data.py` fills your Firebase project with a believable demo world so every screen
looks alive for your presentation:

- **~14 users** with complete profiles + avatars, levels/XP, achievements
- **~45 hikes** all inside **Parco della Caffarella (Rome)** — real-looking GPS tracks, stats,
  photos, and (optionally) bird voice-notes
- **reviews** (some with photos), **comments**, **likes**, and a **follow** graph between users
- the **achievement catalog** so trophies show their names

Everything is written with the **Admin SDK**, so it bypasses your security rules (no need to
log in as each user). Data is internally consistent with the app (levels, XP, calories,
denormalized like/review counts).

---

## 1. Get a service-account key
Firebase Console → ⚙️ **Project settings → Service accounts → Generate new private key** →
save the JSON as `backend/serviceAccount.json`.

> Keep this file private — it grants full admin access. It's already covered by `.gitignore`
> patterns for secrets; double-check it never gets committed.

## 2. Install deps
```bash
cd backend
pip install -r requirements.txt
```

## 3. Run it
```bash
python seed_demo_data.py --service-account serviceAccount.json \
    --bucket wildtrail-dev-4de14.firebasestorage.app
```
(`--bucket` is the `storage_bucket` value from your `google-services.json`. It's only strictly
needed if you add your own media in `seed_assets/` — see below.)

Open the app, go to **Explore**, and the feed is full. 🎉

---

## Make YOUR account look full too (optional)
By default the demo data is "other people". To also populate **your own** Home/Profile:

1. Sign up / log in once in the app (creates your user doc).
2. Find your uid in Firebase Console → **Authentication** (the "User UID" column).
3. Re-run with `--my-uid`:
   ```bash
   python seed_demo_data.py --service-account serviceAccount.json \
       --bucket wildtrail-dev-4de14.firebasestorage.app --my-uid YOUR_UID
   ```
This attributes ~6 hikes + achievements to you and has other users like/comment/review them.
It **merges** your stats — it won't overwrite your username or profile picture.

## Real photos & working BirdNet (optional but recommended)
Out of the box, avatars/photos use public placeholder URLs and hikes have **no** audio.
To use real, thematic media, drop files into `backend/seed_assets/` and re-run:

```
seed_assets/avatars/*.jpg          seed_assets/photos/*.jpg          seed_assets/audio/*.mp3
```
- **Bird audio is required for the "Detect Bird" demo.** Grab a few CC clips from
  <https://xeno-canto.org> (search e.g. `Turdus merula`, `Erithacus rubecula`, `Parus major`)
  into `seed_assets/audio/`. The script uploads them and attaches them to hikes.
- Uploading local files needs `--bucket`.

See `seed_assets/README.md` for details.

---

## Re-running / cleaning up
- IDs are deterministic (`seed_user_*`, `seed_hike_*`), so **re-running overwrites** the same
  demo docs instead of duplicating.
- Remove a previous seed (only the docs this script created — your real account is untouched):
  ```bash
  python seed_demo_data.py --service-account serviceAccount.json --wipe-only \
      --bucket wildtrail-dev-4de14.firebasestorage.app
  ```
  or add `--wipe` to clean then re-seed in one go.

## Tuning
```
--users 20 --hikes 60     # bigger dataset
--seed 42                 # different random world (reproducible)
```

## Notes
- The seeded users are Firestore profiles, not Firebase Auth accounts — that's fine, the app
  only needs them to *display* hikes, reviews, comments and profiles. You present from your
  own logged-in account.
- If photos don't load during the demo, your network may be blocking loremflickr/pravatar —
  drop local JPGs in `seed_assets/photos/` + `avatars/` and re-run to upload them to your own
  Storage instead.
