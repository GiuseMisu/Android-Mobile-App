#!/usr/bin/env python
"""
Seed WildTrail's Firestore (+ optionally Storage) with a rich DEMO dataset:
a group of users (profiles + avatars), hikes inside Parco della Caffarella (Rome),
reviews (some with photos), comments, likes, follows and achievements — so every
screen looks alive for a presentation.

QUICK START
-----------
    cd backend
    pip install -r requirements.txt
    # Firebase console -> Project settings -> Service accounts -> Generate new private key
    python seed_demo_data.py --service-account serviceAccount.json \
        --bucket wildtrail-dev-4de14.firebasestorage.app

Useful flags
------------
    --my-uid <uid>     Also attribute some hikes/achievements to YOUR logged-in account
                       (sign up in the app first; this MERGES, it won't clobber your profile).
                       Find your uid in Firebase console -> Authentication.
    --wipe             Delete a previous seed (only docs this script created) before seeding.
    --users 14 --hikes 45    Tune dataset size.
    --seed 7           RNG seed for reproducibility.

Media
-----
By default avatars come from pravatar.cc and photos from loremflickr (public URLs — the
app loads them directly, no upload needed). Drop your own files to upload & use instead:

    backend/seed_assets/avatars/*.jpg     # profile pictures
    backend/seed_assets/photos/*.jpg      # hike + review photos
    backend/seed_assets/audio/*.mp3|m4a|ogg|wav   # bird recordings (REQUIRED for working BirdNet)

For working "Detect Bird", grab a few CC recordings (e.g. from xeno-canto.org: search
"Turdus merula", "Erithacus rubecula", "Parus major") into seed_assets/audio/.
Uploading requires --bucket.
"""

import argparse
import glob
import math
import mimetypes
import os
import random
import sys
from datetime import datetime, timezone
from urllib.parse import quote

import firebase_admin
from firebase_admin import credentials, firestore, storage

BASE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(BASE, "seed_assets")

# ----------------------------------------------------------------------------- geo
# Parco della Caffarella, Rome — valley of the Almone, low elevation (~25-45 m).
CAFFARELLA = dict(lat_min=41.8585, lat_max=41.8725, lng_min=12.5085, lng_max=12.5235)
BASE_ALT = 33.0

# ----------------------------------------------------------------------------- content pools (Italian)
FIRST = ["Marco", "Giulia", "Luca", "Sofia", "Alessandro", "Martina", "Francesco",
         "Chiara", "Matteo", "Elena", "Davide", "Aurora", "Lorenzo", "Sara",
         "Federico", "Valentina", "Simone", "Giorgia", "Andrea", "Beatrice"]
LAST = ["Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo",
        "Ricci", "Marino", "Greco", "Bruno", "Gallo", "Conti", "De Luca",
        "Mancini", "Costa", "Giordano", "Rizzo", "Lombardi", "Moretti"]
BIOS = [
    "Cammino ogni weekend tra i parchi di Roma 🌳",
    "Appassionato di birdwatching e lunghe passeggiate.",
    "Fotografo dilettante, amo i sentieri all'alba.",
    "Trail runner della domenica 🏃",
    "Esploro la valle dell'Almone un passo alla volta.",
    "Natura, silenzio e una buona borraccia.",
    "Guida ambientale escursionistica in formazione.",
    "Sempre alla ricerca del prossimo sentiero.",
    "Roma a piedi, lontano dal traffico.",
    "Mi rilasso tra ruderi romani e prati verdi.",
    "Camminare è la mia terapia preferita.",
    "Volontaria per la tutela del parco 🌿",
]
COUNTRIES = ["Italy", "Italy", "Italy", "Italy", "Spain", "France", "Germany", "United Kingdom"]
SEXES = ["MALE", "MALE", "FEMALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY"]

TITLES = [
    "Mattina alla Caffarella", "Lungo l'Almone", "Giro del Ninfeo di Egeria",
    "Sentiero della Valle", "Tra i ruderi della Caffarella", "Anello del parco",
    "Tramonto sulla Caffarella", "Birdwatching all'Almone", "Corsa mattutina nel parco",
    "Passeggiata al Casale", "Dal Largo Tacchi al fiume", "Verso il Tempio del Dio Redicolo",
    "Sentiero dei pascoli", "Giro delle sorgenti", "Camminata tra le pecore",
    "Esplorando la Vaccareccia", "Passi lenti nella valle", "Pomeriggio verde a Roma",
    "Anello lungo dell'Appia", "Quiete tra i canneti",
]
DESCRIPTIONS = [
    "Percorso facile e rilassante, perfetto per staccare dalla città.",
    "Tanti uccelli lungo il fiume, portatevi il binocolo.",
    "Sentiero un po' fangoso dopo la pioggia ma molto bello.",
    "Vista stupenda sui ruderi romani, consigliatissimo.",
    "Giro tranquillo, adatto anche ai bambini.",
    "Ho incontrato le pecore al pascolo, esperienza unica.",
    "Acqua disponibile alla fontanella vicino al casale.",
    "Percorso ad anello ben segnalato, ottimo per correre.",
    "Un angolo di campagna dentro Roma, da non perdere.",
    "Tratto finale in salita leggera, niente di impegnativo.",
    None,
    None,
]
REVIEW_TEXTS = [
    "Bellissimo percorso, tornerò sicuramente!", "Un po' fangoso ma ne vale la pena.",
    "Tanti uccelli, ho registrato dei canti stupendi.", "Tracciato preciso, grazie per la condivisione!",
    "Ideale per una corsa leggera la mattina.", "Sentiero ben tenuto e tranquillo.",
    "Ottimo per portare i bambini, facile e sicuro.", "Vista sui ruderi davvero suggestiva.",
    "Mancava un po' d'ombra a mezzogiorno.", "Acqua disponibile, comodo nelle giornate calde.",
    "Percorso rilassante, consigliato!", "Un classico della Caffarella, sempre piacevole.",
    "Attenzione a qualche tratto scivoloso.", "Perfetto per disconnettere dalla città.",
    "Pecore al pascolo lungo il cammino, bellissimo.", "Buon dislivello per essere un parco urbano.",
]
COMMENT_TEXTS = [
    "Che bel giro! 🌿", "Grazie per la traccia, la provo questo weekend.",
    "Anch'io adoro la Caffarella la mattina presto.", "Le foto sono stupende!",
    "Hai visto anche gli aironi vicino al fiume?", "Quanta acqua avete trovato in giro?",
    "Bel ritmo, complimenti!", "Ci sono andato ieri, posto magnifico.",
    "Consigli sulle scarpe per il fango?", "Aggiungo questo sentiero ai preferiti.",
    "Top come sempre 👏", "Che pace in mezzo a Roma.",
    "L'audio degli uccelli è fantastico!", "Bravissimo, continua così!",
    "Una delle mie zone preferite per correre.", "Grazie per aver segnalato la fontanella.",
]
SURFACES = ["FOREST", "FOREST", "OTHER", "URBAN", "FOREST", "OTHER"]


# ----------------------------------------------------------------------------- helpers
def now_ms():
    # Real current time, so seeded hikes always look recent whenever you run the script.
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def dob_millis(year):
    return int(datetime(year, 6, 15, tzinfo=timezone.utc).timestamp() * 1000)


def haversine_m(a, b):
    R = 6_371_000.0
    lat1, lat2 = math.radians(a["lat"]), math.radians(b["lat"])
    dlat = lat2 - lat1
    dlng = math.radians(b["lng"] - a["lng"])
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(h), math.sqrt(1 - h))


def gen_route(rng, started_at):
    """A plausible walking track that stays inside the Caffarella bounding box."""
    lat = rng.uniform(CAFFARELLA["lat_min"] + 0.002, CAFFARELLA["lat_max"] - 0.002)
    lng = rng.uniform(CAFFARELLA["lng_min"] + 0.002, CAFFARELLA["lng_max"] - 0.002)
    alt = BASE_ALT + rng.uniform(-3, 3)
    t = started_at
    heading = rng.uniform(0, 2 * math.pi)
    n = rng.randint(25, 120)
    pts = []
    for _ in range(n):
        pts.append({"lat": round(lat, 6), "lng": round(lng, 6),
                    "altitudeM": round(alt, 1), "timestamp": int(t)})
        heading += rng.uniform(-0.6, 0.6)
        step = rng.uniform(0.00018, 0.00034)  # ~20-38 m
        lat += step * math.cos(heading)
        lng += step * math.sin(heading) / math.cos(math.radians(lat))
        # keep inside the park: bounce the heading if we hit an edge
        if not (CAFFARELLA["lat_min"] < lat < CAFFARELLA["lat_max"]):
            lat = min(max(lat, CAFFARELLA["lat_min"] + 0.001), CAFFARELLA["lat_max"] - 0.001)
            heading += math.pi / 2
        if not (CAFFARELLA["lng_min"] < lng < CAFFARELLA["lng_max"]):
            lng = min(max(lng, CAFFARELLA["lng_min"] + 0.001), CAFFARELLA["lng_max"] - 0.001)
            heading += math.pi / 2
        alt += rng.uniform(-1.3, 1.4)
        t += rng.randint(18_000, 42_000)
    return pts


def route_length_km(pts):
    return sum(haversine_m(pts[i - 1], pts[i]) for i in range(1, len(pts))) / 1000.0


def elevation_gain_m(pts):
    gain = 0.0
    for i in range(1, len(pts)):
        d = pts[i]["altitudeM"] - pts[i - 1]["altitudeM"]
        if d > 0:
            gain += d
    return int(gain)


def level_for_xp(xp):  # mirrors LevelMath.levelForXp
    if xp <= 0:
        return 1
    return max(1, int((1.0 + math.sqrt(1.0 + 0.08 * xp)) / 2.0))


def xp_from_hike(distance_km, elev_m):  # mirrors HikeMath.xpFromHike
    return max(10, int(distance_km * 10) + int(elev_m / 100 * 5) + 5)


def calories(distance_km, elev_m):  # mirrors HikeMath.estimateCalories
    return int(distance_km * 80 + elev_m * 0.5)


# Achievements (id, threshold) — must match AchievementCatalog.
ACH_DISTANCE = [("distance_first_km", 1), ("distance_10_km", 10),
                ("distance_50_km", 50), ("distance_200_km", 200)]
ACH_STREAK = [("streak_5_hikes", 5), ("streak_25_hikes", 25), ("streak_100_hikes", 100)]
ACH_SOCIAL = [("social_first_public", 1), ("social_10_public", 10)]
ACH_DEFS = [
    ("distance_first_km", "First Steps", "Record your first kilometre.", 20, "DISTANCE", 1.0),
    ("distance_10_km", "Day Hiker", "Hike a cumulative 10 km.", 50, "DISTANCE", 10.0),
    ("distance_50_km", "Trail Regular", "Hike a cumulative 50 km.", 100, "DISTANCE", 50.0),
    ("distance_200_km", "Long Hauler", "Hike a cumulative 200 km.", 250, "DISTANCE", 200.0),
    ("elevation_500m", "Hill Climber", "Climb 500 m of elevation in a single hike.", 75, "ELEVATION", 500.0),
    ("elevation_1000m", "Mountain Goat", "Climb 1000 m of elevation in a single hike.", 200, "ELEVATION", 1000.0),
    ("elevation_2000m", "Sky Walker", "Climb 2000 m of elevation in a single hike.", 400, "ELEVATION", 2000.0),
    ("social_first_public", "Going Public", "Share your first public hike.", 20, "SOCIAL", 1.0),
    ("social_10_public", "Community Voice", "Share 10 public hikes.", 100, "SOCIAL", 10.0),
    ("streak_5_hikes", "Getting Hooked", "Complete 5 hikes.", 40, "STREAK", 5.0),
    ("streak_25_hikes", "Trail Dedication", "Complete 25 hikes.", 150, "STREAK", 25.0),
    ("streak_100_hikes", "Trail Master", "Complete 100 hikes.", 500, "STREAK", 100.0),
]


# ----------------------------------------------------------------------------- media
class Media:
    """Resolves avatar/photo/audio URLs, uploading local seed_assets when present."""

    def __init__(self, bucket):
        self.bucket = bucket
        self.avatars = self._prepare("avatars", ["jpg", "jpeg", "png"],
                                     [f"https://i.pravatar.cc/400?img={i}" for i in range(1, 71)])
        self.photos = self._prepare("photos", ["jpg", "jpeg", "png"],
                                    [f"https://loremflickr.com/1080/720/forest,park,trail,nature/?lock={i}"
                                     for i in range(1, 31)])
        self.audio = self._prepare("audio", ["mp3", "m4a", "ogg", "wav"], [])
        if not self.audio:
            print("  ! No audio in seed_assets/audio/ — hikes will have NO bird recordings.\n"
                  "    Add a few CC bird clips there (and pass --bucket) for working BirdNet.")

    def _prepare(self, subdir, exts, fallback):
        files = []
        for ext in exts:
            files += glob.glob(os.path.join(ASSETS, subdir, f"*.{ext}"))
        files = sorted(files)
        if not files:
            return fallback
        if self.bucket is None:
            print(f"  ! Found local {subdir} but no --bucket; using public URLs instead.")
            return fallback
        urls = []
        for path in files:
            name = os.path.basename(path)
            urls.append(self._upload(path, f"seed/{subdir}/{name}"))
        print(f"  uploaded {len(urls)} {subdir} file(s) to Storage")
        return urls

    def _upload(self, local_path, dest_path):
        blob = self.bucket.blob(dest_path)
        token = "%032x" % random.getrandbits(128)
        blob.metadata = {"firebaseStorageDownloadTokens": token}
        ctype = mimetypes.guess_type(local_path)[0] or "application/octet-stream"
        blob.upload_from_filename(local_path, content_type=ctype)
        return (f"https://firebasestorage.googleapis.com/v0/b/{self.bucket.name}"
                f"/o/{quote(dest_path, safe='')}?alt=media&token={token}")


# ----------------------------------------------------------------------------- generation
def build_dataset(rng, media, n_users, n_hikes, my_uid, primary_username, primary_pfp):
    ops = []  # (collection, doc_id, data)

    def add(coll, doc_id, data):
        data["_seed"] = True
        ops.append((coll, doc_id, data))

    # ---- users
    users = []  # dicts with uid/username/pfp + running stats
    for i in range(n_users):
        uid = f"seed_user_{i:02d}"
        first, last = rng.choice(FIRST), rng.choice(LAST)
        username = f"{first}_{last}".replace(" ", "")
        pfp = media.avatars[i % len(media.avatars)]
        users.append({
            "uid": uid, "username": username, "pfp": pfp,
            "sex": rng.choice(SEXES),
            "dateOfBirth": dob_millis(rng.randint(1965, 2006)),
            "country": rng.choice(COUNTRIES),
            "bio": rng.choice(BIOS),
            "createdAt": now_ms() - rng.randint(60, 400) * 86_400_000,
            # stats accumulated below
            "xp": 0, "distance": 0.0, "hikes": 0, "public": 0,
        })

    # the logged-in presenter, if provided, behaves like an extra creator
    primary = None
    if my_uid:
        primary = {"uid": my_uid, "username": primary_username or "You",
                   "pfp": primary_pfp, "xp": 0, "distance": 0.0, "hikes": 0, "public": 0}

    creators = users + ([primary] if primary else [])

    # ---- hikes
    hikes = []
    for h in range(n_hikes):
        # give the presenter the first few hikes so their Home/Profile looks full
        if primary and h < 6:
            creator = primary
        else:
            creator = rng.choice(users)
        hid = f"seed_hike_{h:03d}"
        started = now_ms() - rng.randint(1, 70) * 86_400_000 - rng.randint(0, 36_000_000)
        route = gen_route(rng, started)
        length = route_length_km(route)
        elev = elevation_gain_m(route)
        ended = route[-1]["timestamp"]
        duration = max(1, (ended - started) // 1000)
        avg_speed = length / (duration / 3600.0) if duration else 0.0
        is_private = rng.random() < 0.12
        xp = xp_from_hike(length, elev)

        # media: 0-3 photos + maybe one bird audio note, located along the route
        media_items = []
        for _ in range(rng.randint(0, 3)):
            p = rng.choice(route)
            media_items.append({
                "id": "%032x" % rng.getrandbits(128), "type": "PHOTO",
                "filePath": rng.choice(media.photos),
                "lat": p["lat"], "lng": p["lng"], "timestamp": p["timestamp"],
            })
        if media.audio and rng.random() < 0.55:
            p = rng.choice(route)
            media_items.append({
                "id": "%032x" % rng.getrandbits(128), "type": "AUDIO",
                "filePath": rng.choice(media.audio),
                "lat": p["lat"], "lng": p["lng"], "timestamp": p["timestamp"],
            })
        cover = next((m["filePath"] for m in media_items if m["type"] == "PHOTO"), None)

        hike = {
            "id": hid, "creator": creator, "isPrivate": is_private,
            "length": length, "elev": elev, "xp": xp, "ended": ended,
            "reviews": [], "likeUids": [],
            "doc": {
                "hikeId": hid,
                "creatorFirebaseUid": creator["uid"],
                "creatorUsername": creator["username"],
                "creatorProfilePictureUrl": creator["pfp"],
                "workoutId": None,
                "title": rng.choice(TITLES),
                "description": rng.choice(DESCRIPTIONS),
                "avgSpeedKmh": float(round(avg_speed, 2)),
                "stepCount": int(length * 1350),
                "caloriesBurned": calories(length, elev),
                "coverPhotoUrl": cover,
                "xpEarned": xp,
                "likesCount": 0,
                "surfaceType": rng.choice(SURFACES),
                "lengthKm": float(round(length, 3)),
                "durationSeconds": int(duration),
                "startedAt": int(started),
                "endedAt": int(ended),
                "elevationGainMeters": elev,
                "routeCoordinates": route,
                "isPrivate": is_private,
                "difficultyLevel": rng.randint(1, 4),
                "mudRisk": rng.randint(1, 4),
                "pathClarity": rng.randint(2, 5),
                "fatigueLevel": rng.randint(1, 4),
                "animalEncounterRisk": rng.randint(1, 3),
                "waterAvailability": rng.random() < 0.5,
                "averageRating": 0.0,
                "reviewCount": 0,
                "mediaItems": media_items,
            },
        }
        creator["hikes"] += 1
        creator["distance"] += length
        creator["xp"] += xp
        if not is_private:
            creator["public"] += 1
        hikes.append(hike)

    # ---- reviews, comments, likes on PUBLIC hikes
    for hike in hikes:
        if hike["isPrivate"]:
            continue
        others = [u for u in users if u["uid"] != hike["creator"]["uid"]]
        # reviews (distinct authors, never the creator)
        n_rev = rng.randint(0, 4)
        ratings = []
        for r, reviewer in enumerate(rng.sample(others, min(n_rev, len(others)))):
            rid = f"seed_review_{hike['id']}_{r}"
            rating = rng.randint(3, 5)
            ratings.append(rating)
            imgs = [rng.choice(media.photos) for _ in range(rng.randint(0, 2))]
            add("reviews", rid, {
                "reviewId": rid, "reviewerUid": reviewer["uid"], "hikeId": hike["id"],
                "overallRating": rating,
                "fatigueLevel": rng.randint(1, 4), "pathClarity": rng.randint(2, 5),
                "difficultyLevel": rng.randint(1, 4), "mudRisk": rng.randint(1, 4),
                "animalEncounterRisk": rng.randint(1, 3),
                "waterAvailability": rng.random() < 0.5,
                "commentText": rng.choice(REVIEW_TEXTS),
                "imageUrls": imgs,
                "createdAt": hike["ended"] + rng.randint(1, 20) * 3_600_000,
            })
        if ratings:
            hike["doc"]["reviewCount"] = len(ratings)
            hike["doc"]["averageRating"] = float(round(sum(ratings) / len(ratings), 2))

        # comments
        for c in range(rng.randint(0, 5)):
            author = rng.choice(users)
            cid = f"seed_comment_{hike['id']}_{c}"
            add("comments", cid, {
                "commentId": cid, "authorUid": author["uid"], "hikeId": hike["id"],
                "text": rng.choice(COMMENT_TEXTS), "photoUrls": [],
                "createdAt": hike["ended"] + rng.randint(1, 30) * 3_600_000,
            })

        # likes
        likers = rng.sample(others, min(rng.randint(2, 10), len(others)))
        for u in likers:
            lid = f"{u['uid']}_{hike['id']}"
            add("likes", lid, {
                "userUid": u["uid"], "hikeId": hike["id"],
                "createdAt": hike["ended"] + rng.randint(1, 48) * 3_600_000,
            })
        hike["doc"]["likesCount"] = len(likers)

    # ---- write hike docs (after counts settled)
    for hike in hikes:
        add("hikes", hike["id"], hike["doc"])

    # ---- follows (a connected-ish graph among synthetic users)
    for u in users:
        targets = rng.sample([o for o in users if o["uid"] != u["uid"]],
                             min(rng.randint(2, 6), len(users) - 1))
        for t in targets:
            fid = f"{u['uid']}_{t['uid']}"
            add("follows", fid, {
                "followerUid": u["uid"], "followeeUid": t["uid"],
                "notifyOnNewHike": rng.random() < 0.5,
                "createdAt": now_ms() - rng.randint(1, 90) * 86_400_000,
            })
    # a few synthetic users follow the presenter
    if primary:
        for u in rng.sample(users, min(5, len(users))):
            fid = f"{u['uid']}_{primary['uid']}"
            add("follows", fid, {
                "followerUid": u["uid"], "followeeUid": primary["uid"],
                "notifyOnNewHike": True, "createdAt": now_ms() - rng.randint(1, 60) * 86_400_000,
            })

    # ---- user docs + achievements (stats now final)
    def award(uid, created_at):
        person = next((p for p in creators if p["uid"] == uid), None)
        if not person:
            return
        earned = []
        for aid, thr in ACH_DISTANCE:
            if person["distance"] >= thr:
                earned.append(aid)
        for aid, thr in ACH_STREAK:
            if person["hikes"] >= thr:
                earned.append(aid)
        for aid, thr in ACH_SOCIAL:
            if person["public"] >= thr:
                earned.append(aid)
        for aid in earned:
            add("user_achievements", f"{uid}_{aid}", {
                "userUid": uid, "achievementId": aid,
                "earnedAt": created_at + rng.randint(1, 30) * 86_400_000,
            })

    for u in users:
        add("users", u["uid"], {
            "firebaseUid": u["uid"], "username": u["username"],
            "sex": u["sex"], "dateOfBirth": u["dateOfBirth"], "country": u["country"],
            "level": level_for_xp(u["xp"]), "xpPoints": u["xp"],
            "totalDistanceKm": float(round(u["distance"], 2)),
            "totalHikesCount": u["hikes"],
            "profilePictureUrl": u["pfp"], "bio": u["bio"],
            "emergencyContactNumber": "9999999",
            "createdAt": u["createdAt"], "lastActive": now_ms(),
            "isPublic": True,
        })
        award(u["uid"], u["createdAt"])

    return ops, primary, hikes


# ----------------------------------------------------------------------------- write / wipe
def commit(db, ops):
    for i in range(0, len(ops), 400):
        batch = db.batch()
        for coll, doc_id, data in ops[i:i + 400]:
            batch.set(db.collection(coll).document(doc_id), data)
        batch.commit()
        print(f"  committed {min(i + 400, len(ops))}/{len(ops)} docs")


def merge_primary(db, primary):
    """Refresh only the presenter's stats — never clobber their real username/picture."""
    db.collection("users").document(primary["uid"]).set({
        "firebaseUid": primary["uid"],
        "level": level_for_xp(primary["xp"]),
        "xpPoints": primary["xp"],
        "totalDistanceKm": float(round(primary["distance"], 2)),
        "totalHikesCount": primary["hikes"],
        "lastActive": now_ms(),
        "isPublic": True,
    }, merge=True)
    # award the presenter their achievements too
    ops = []
    for aid, thr in ACH_DISTANCE + ACH_STREAK + ACH_SOCIAL:
        ok = ((aid, thr) in ACH_DISTANCE and primary["distance"] >= thr) or \
             ((aid, thr) in ACH_STREAK and primary["hikes"] >= thr) or \
             ((aid, thr) in ACH_SOCIAL and primary["public"] >= thr)
        if ok:
            ops.append(("user_achievements", f"{primary['uid']}_{aid}",
                        {"userUid": primary["uid"], "achievementId": aid,
                         "earnedAt": now_ms(), "_seed": True}))
    commit(db, ops)


def wipe(db, bucket):
    print("Wiping previous seed (docs with _seed == True)…")
    total = 0
    for coll in ["users", "hikes", "reviews", "comments", "likes", "follows",
                 "user_achievements", "achievement_definitions"]:
        docs = list(db.collection(coll).where("_seed", "==", True).stream())
        for i in range(0, len(docs), 400):
            batch = db.batch()
            for d in docs[i:i + 400]:
                batch.delete(d.reference)
            batch.commit()
        total += len(docs)
        if docs:
            print(f"  {coll}: deleted {len(docs)}")
    if bucket is not None:
        blobs = list(bucket.list_blobs(prefix="seed/"))
        for b in blobs:
            b.delete()
        if blobs:
            print(f"  storage: deleted {len(blobs)} seeded file(s)")
    print(f"Wipe done ({total} docs).")


# ----------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description="Seed WildTrail with a demo dataset.")
    ap.add_argument("--service-account", required=True, help="Path to the Firebase service account JSON.")
    ap.add_argument("--bucket", default=None, help="Storage bucket (only needed to upload local seed_assets).")
    ap.add_argument("--users", type=int, default=14)
    ap.add_argument("--hikes", type=int, default=45)
    ap.add_argument("--my-uid", default=None, help="Your Firebase Auth uid to enrich your own account.")
    ap.add_argument("--seed", type=int, default=7, help="RNG seed for reproducibility.")
    ap.add_argument("--wipe", action="store_true", help="Delete a previous seed before seeding.")
    ap.add_argument("--wipe-only", action="store_true", help="Only wipe, don't seed.")
    args = ap.parse_args()

    if not os.path.exists(args.service_account):
        sys.exit(f"Service account file not found: {args.service_account}")

    opts = {}
    if args.bucket:
        opts["storageBucket"] = args.bucket
    firebase_admin.initialize_app(credentials.Certificate(args.service_account), opts)
    db = firestore.client()
    bucket = storage.bucket() if args.bucket else None

    if args.wipe or args.wipe_only:
        wipe(db, bucket)
        if args.wipe_only:
            return

    print("Preparing media…")
    media = Media(bucket)

    primary_username, primary_pfp = "You", None
    if args.my_uid:
        snap = db.collection("users").document(args.my_uid).get()
        if snap.exists:
            d = snap.to_dict() or {}
            primary_username = d.get("username", "You")
            primary_pfp = d.get("profilePictureUrl")
        else:
            print(f"  ! users/{args.my_uid} doesn't exist yet — sign up in the app first so your "
                  f"profile is created, otherwise your name/picture may be blank.")

    rng = random.Random(args.seed)
    print(f"Generating dataset: {args.users} users, {args.hikes} hikes in Parco della Caffarella…")
    ops, primary, hikes = build_dataset(
        rng, media, args.users, args.hikes, args.my_uid, primary_username, primary_pfp)

    # achievement catalog (needed for the app to show names/descriptions)
    for aid, name, desc, xp, cat, thr in ACH_DEFS:
        ops.append(("achievement_definitions", aid, {
            "achievementId": aid, "name": name, "description": desc, "iconUrl": None,
            "xpReward": xp, "category": cat, "thresholdValue": thr, "_seed": True,
        }))

    print(f"Writing {len(ops)} documents to Firestore…")
    commit(db, ops)
    if primary:
        print(f"Enriching your account ({args.my_uid})…")
        merge_primary(db, primary)

    public = sum(1 for h in hikes if not h["isPrivate"])
    print("\nDone ✅")
    print(f"  users:   {args.users}")
    print(f"  hikes:   {args.hikes} ({public} public, shown in the feed)")
    print(f"  + reviews / comments / likes / follows / achievements")
    print("  Log in to the app and open Explore to see the demo data.")


if __name__ == "__main__":
    main()
