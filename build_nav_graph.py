#!/usr/bin/env python
"""Compose the WildTrail navigation graph from the app screenshots."""
import math
from PIL import Image, ImageDraw, ImageFont

SRC = "images_for_nav_graph"
S = 1.7                 # global scale (bigger image + higher resolution)
H = round(1000 * S)     # target thumbnail height

def sc(v):  return round(v * S)
def Q(x, y): return (round(x * S), round(y * S))

# ---------- fonts ----------
def font(path, size):
    try:
        return ImageFont.truetype(path, round(size * S))
    except Exception:
        return ImageFont.load_default()

F_TITLE = font("C:/Windows/Fonts/arialbd.ttf", 84)
F_BAND  = font("C:/Windows/Fonts/arialbd.ttf", 44)
F_NODE  = font("C:/Windows/Fonts/arialbd.ttf", 42)
F_LBL   = font("C:/Windows/Fonts/arialbd.ttf", 34)

# ---------- colours ----------
BG     = (247, 249, 248)
INK    = (22, 28, 26)
ACCENT = (33, 140, 95)    # primary green flow
BLUE   = (40, 90, 170)    # "open detail" flows
RED    = (185, 55, 55)    # logout
GREY   = (120, 128, 124)

# ---------- nodes: name -> (file, center_x, top_y, label) ----------
NODES = {
    "login":   ("log_in_screen.jpeg",                 560,  290, "Login"),
    "signup":  ("signup_screen.jpeg",                1180,  290, "Sign up  (same screen)"),

    "home":    ("home_screen.jpeg",                   560, 1560, "Home"),
    "explore": ("explore_featured_social_screen.jpeg",1120,1560, "Explore"),
    "track":   ("track_hike_screen.jpeg",            1760, 1560, "Track  (idle)"),
    "profile": ("profile_screen.jpeg",               2900, 1560, "Profile"),

    "detail":  ("saved_hike_card_screen.jpeg",        820, 3030, "Hike Detail"),
    "active":  ("active_tracking_hike_screen.jpeg",  1760, 3030, "Tracking  (recording)"),
    "ach":     ("achievements_screen.jpeg",          2450, 3030, "Achievements"),
    "settings":("edit_profile_screen.jpeg",          2920, 3030, "Edit Profile"),
    "liked":   ("liked_hikes_screen.jpeg",           3380, 3030, "Liked Hikes"),

    "review":  ("hike_review_screen.jpeg",            820, 4300, "Submit Review"),
    "save":    ("save_tracked_hike_screen.jpeg",     1760, 4300, "Save Hike  (sheet)"),
}

W, CH = sc(3680), sc(5500)

# ---------- load & scale ----------
placed = {}; imgs = {}
for name,(f,cx,ty,lbl) in NODES.items():
    im = Image.open(f"{SRC}/{f}").convert("RGB")
    w = round(im.width * H / im.height)
    imgs[name] = im.resize((w, H), Image.LANCZOS)
    cxs, tys = sc(cx), sc(ty)
    placed[name] = (cxs - w//2, tys, cxs - w//2 + w, tys + H)

canvas = Image.new("RGB", (W, CH), BG)
d = ImageDraw.Draw(canvas)

def anchor(name, side):
    x0,y0,x1,y1 = placed[name]; cx=(x0+x1)//2; cy=(y0+y1)//2
    return {"top":(cx,y0),"bottom":(cx,y1),"left":(x0,cy),"right":(x1,cy),
            "tl":(x0,y0),"tr":(x1,y0),"bl":(x0,y1),"br":(x1,y1)}[side]

# ---------- edges (lines first, labels deferred & drawn LAST on top) ----------
LABELS = []  # (x, y, text, color)

def arrowhead(p, ang, color, size=34, width=8):
    x,y=p; size=sc(size); width=sc(width)
    for s in (-1,1):
        a=ang+s*math.radians(25)
        d.line([(x,y),(x-size*math.cos(a),y-size*math.sin(a))], fill=color, width=width)

def seg(p1,p2,color,width,dashed,head):
    x1,y1=p1; x2,y2=p2; width=sc(width)
    if dashed:
        n=max(2,int(math.hypot(x2-x1,y2-y1)//sc(30)))
        for i in range(n):
            if i%2: continue
            ax=x1+(x2-x1)*i/n; ay=y1+(y2-y1)*i/n
            bx=x1+(x2-x1)*(i+1)/n; by=y1+(y2-y1)*(i+1)/n
            d.line([(ax,ay),(bx,by)],fill=color,width=width)
    else:
        d.line([p1,p2],fill=color,width=width)
    if head:
        arrowhead(p2, math.atan2(y2-y1,x2-x1), color)

def edge(p1,p2,color=ACCENT,width=8,dashed=False,head=True,label=None,lblpos=0.5,loff=(0,0)):
    seg(p1,p2,color,width,dashed,head)
    if label:
        x1,y1=p1; x2,y2=p2
        LABELS.append((x1+(x2-x1)*lblpos+sc(loff[0]), y1+(y2-y1)*lblpos+sc(loff[1]), label, color))

# auth toggle (two offset arrows + one deferred label)
lr = anchor("login","right"); sl = anchor("signup","left"); o = sc(22)
edge((lr[0],lr[1]-o),(sl[0],sl[1]-o),GREY,6)
edge((sl[0],sl[1]+o),(lr[0],lr[1]+o),GREY,6)
LABELS.append(((lr[0]+sl[0])//2, lr[1]-sc(86), "tap to toggle", GREY))

# login success -> home
edge(anchor("login","bottom"), anchor("home","top"), ACCENT, 9,
     label="log in / sign-up", lblpos=0.5, loff=(200,0))

# logout: profile -> up -> left -> login (orthogonal, red dashed)
pt = anchor("profile","top"); lt = anchor("login","top"); ylog = sc(200)
edge(pt,(pt[0],ylog),RED,6,dashed=True,head=False)
edge((pt[0],ylog),(lt[0],ylog),RED,6,dashed=True,head=False)
edge((lt[0],ylog),lt,RED,6,dashed=True,head=True)
LABELS.append((*Q(1900,235), "Logout  ->  AuthGraph", RED))

# bottom-nav hub bar
bx0 = anchor("home","bottom")[0]-sc(140); bx1 = anchor("profile","bottom")[0]+sc(140)
by0, by1 = sc(2650), sc(2744)
for t in ("home","explore","track","profile"):
    tx=anchor(t,"bottom")[0]
    d.line([(tx,anchor(t,"bottom")[1]),(tx,by0)],fill=ACCENT,width=sc(7))
d.rounded_rectangle([bx0,by0,bx1,by1], radius=sc(40), fill=(214,232,224), outline=ACCENT, width=sc(5))
bt = "<  Bottom navigation  -  switch tabs freely  >"
tb=d.textbbox((0,0),bt,font=F_NODE)
d.text(((bx0+bx1)//2-(tb[2]-tb[0])//2, (by0+by1)//2-sc(26)), bt, font=F_NODE, fill=ACCENT)

# tabs -> details (start from nav bar)
edge((anchor("home","bottom")[0],by1),    Q(790,3030),  BLUE,8, label="open hike card", lblpos=.5, loff=(-40,-10))
edge((anchor("explore","bottom")[0],by1), Q(850,3030),  BLUE,8)
edge((anchor("track","bottom")[0],by1),   anchor("active","top"), ACCENT,9, label="Start hike", loff=(190,0))
edge((anchor("profile","bottom")[0],by1), Q(2500,3030), BLUE,8, label="trophy / See all", lblpos=.5, loff=(-90,-40))
edge((anchor("profile","bottom")[0],by1), anchor("settings","top"), BLUE,8, label="gear icon", lblpos=.5, loff=(170,-66))
edge((anchor("profile","bottom")[0],by1), Q(3360,3030), BLUE,8, label="globe icon", lblpos=.5, loff=(120,30))

# detail -> review ; active -> save
edge(anchor("detail","bottom"), anchor("review","top"), BLUE,8, label="Add review", loff=(190,0))
edge(anchor("active","bottom"), anchor("save","top"),   ACCENT,9, label="Finish", loff=(160,0))

# ---------- thumbnails + title chips ----------
for name,(f,cx,ty,lbl) in NODES.items():
    x0,y0,x1,y1 = placed[name]
    canvas.paste(imgs[name],(x0,y0))
    d.rectangle([x0-sc(4),y0-sc(4),x1+sc(4),y1+sc(4)],outline=INK,width=sc(5))
    tb=d.textbbox((0,0),lbl,font=F_NODE); tw=tb[2]-tb[0]; cxm=(x0+x1)//2
    d.rounded_rectangle([cxm-tw/2-sc(20),y0-sc(72),cxm+tw/2+sc(20),y0-sc(14)],radius=sc(16), fill=INK)
    d.text((cxm-tw/2,y0-sc(66)),lbl,font=F_NODE,fill=(255,255,255))

# ---------- section labels (left margin, clear of nodes) ----------
d.multiline_text(Q(40, 720), "AUTH\nAuthGraph",          font=F_BAND, fill=GREY, spacing=sc(10))
d.multiline_text(Q(40,1980), "MAIN\nMainGraph\n+ bottom nav", font=F_BAND, fill=GREY, spacing=sc(10))
d.multiline_text(Q(40,3360), "DETAIL\nflows",            font=F_BAND, fill=GREY, spacing=sc(10))

# ---------- edge labels LAST (opaque boxes, never crossed by lines) ----------
for lx,ly,text,color in LABELS:
    tb=d.textbbox((0,0),text,font=F_LBL); tw=tb[2]-tb[0]; th=tb[3]-tb[1]; p=sc(14)
    d.rounded_rectangle([lx-tw/2-p,ly-th/2-p-sc(2),lx+tw/2+p,ly+th/2+p],radius=sc(12),
                        fill=(255,255,255),outline=color,width=sc(4))
    d.text((lx-tw/2,ly-th/2-tb[1]),text,font=F_LBL,fill=color)

# ---------- title ----------
t="WildTrail  -  Navigation Graph"
tb=d.textbbox((0,0),t,font=F_TITLE); d.text(((W-(tb[2]-tb[0]))//2,sc(46)),t,font=F_TITLE,fill=INK)

canvas.save("nav_graph.png")
print("saved nav_graph.png", canvas.size)
