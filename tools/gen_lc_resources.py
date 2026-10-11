#!/usr/bin/env python3
"""Generates the 'Liquid Crystal' drawable + vector icon set for the LUCIFER launcher UI.

Run from the repository root:
    python3 tools/gen_lc_resources.py

Every generated file is a plain Android XML drawable (shape / layer-list / ripple / vector),
so the whole design system is GPU-cheap: no bitmaps, no runtime blur, no per-frame allocation.
"""
import os

RES = os.path.join("app_pojavlauncher", "src", "main", "res")
DRAWABLE = os.path.join(RES, "drawable")

# ── palette ──────────────────────────────────────────────────────────────────
VOID = "#FF06070F"
CYAN = "#FF35E6FF"
VIOLET = "#FF8B6CFF"
MAGENTA = "#FFFF4FD8"
MINT = "#FF7CFFCB"
GOLD = "#FFE8C56A"
GREEN = "#FF35E06A"
RED = "#FFFF6B6B"
TEXT = "#FFEAF0FF"

GLASS_1 = "#0DFFFFFF"   # 5%
GLASS_2 = "#12FFFFFF"   # 7%
GLASS_3 = "#1AFFFFFF"   # 10%
GLASS_4 = "#26FFFFFF"   # 15%
RIM_1 = "#1FFFFFFF"     # 12%
RIM_2 = "#2EFFFFFF"     # 18%
RIM_3 = "#40FFFFFF"     # 25%

CYAN_A = "#3335E6FF"
CYAN_B = "#5535E6FF"
VIOLET_A = "#338B6CFF"
MAGENTA_A = "#33FF4FD8"
MINT_A = "#337CFFCB"

FILES = {}


def add(name, xml):
    FILES[name] = xml.strip() + "\n"


def shape(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
            + body + "</shape>")


def glass_solid(fill, radius, stroke_color=RIM_2, stroke_w="1dp"):
    return shape(
        '    <solid android:color="%s" />\n' % fill +
        '    <corners android:radius="%s" />\n' % radius +
        '    <stroke android:width="%s" android:color="%s" />' % (stroke_w, stroke_color))


# ══════════════════════════════════════════════════════════════════════════════
# 1. SURFACES — frosted glass slabs
# ══════════════════════════════════════════════════════════════════════════════

# Large sheet (dialogs, big cards). Gradient fill fakes the frosted depth.
add("lc_glass_sheet", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <corners android:radius="@dimen/lc_radius_sheet" />
            <gradient
                android:angle="270"
                android:startColor="#1FFFFFFF"
                android:centerColor="#14FFFFFF"
                android:endColor="#0AFFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
    <!-- top specular highlight -->
    <item android:top="0dp" android:bottom="0dp">
        <shape>
            <corners android:radius="@dimen/lc_radius_sheet" />
            <gradient
                android:angle="270"
                android:startColor="#14FFFFFF"
                android:endColor="#00FFFFFF" />
        </shape>
    </item>
</layer-list>""")

# Standard card
add("lc_glass_card", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <corners android:radius="@dimen/lc_radius_card" />
            <gradient
                android:angle="270"
                android:startColor="#17FFFFFF"
                android:endColor="#0AFFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>""")

# Active / focused card — chromatic rim
add("lc_glass_card_active", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- chromatic rim -->
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/lc_radius_card" />
            <gradient
                android:angle="315"
                android:startColor="@color/lc_cyan"
                android:centerColor="@color/lc_magenta"
                android:endColor="@color/lc_violet" />
        </shape>
    </item>
    <!-- glass body inset by the rim width -->
    <item android:left="1.5dp" android:top="1.5dp" android:right="1.5dp" android:bottom="1.5dp">
        <shape>
            <corners android:radius="@dimen/lc_radius_card" />
            <gradient
                android:angle="270"
                android:startColor="#2635E6FF"
                android:centerColor="#1A8B6CFF"
                android:endColor="#1406070F" />
        </shape>
    </item>
</layer-list>""")

# Subtle row (list items, settings rows)
add("lc_glass_row", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <corners android:radius="@dimen/lc_radius_row" />
            <solid android:color="#12FFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>""")

# Capsule (pills, chips, top bar)
add("lc_glass_capsule", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient
                android:angle="270"
                android:startColor="#1AFFFFFF"
                android:endColor="#0DFFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>""")

# Floating dock capsule — denser glass
add("lc_glass_dock", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient
                android:angle="270"
                android:startColor="#2AFFFFFF"
                android:centerColor="#1AFFFFFF"
                android:endColor="#12FFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim_strong" />
        </shape>
    </item>
</layer-list>""")

# Inset field for search
add("lc_glass_field", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <corners android:radius="@dimen/lc_radius_field" />
            <solid android:color="#0FFFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>""")

# Dashed "create / new" tile
add("lc_dashed_tile", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#0A35E6FF" />
    <corners android:radius="@dimen/lc_radius_card" />
    <stroke
        android:width="1.5dp"
        android:color="#7035E6FF"
        android:dashWidth="7dp"
        android:dashGap="5dp" />
</shape>""")

# Icon tile (settings rows, grid cards)
add("lc_icon_tile", glass_solid("#16FFFFFF", "@dimen/lc_radius_tile", RIM_2))

# Divider
add("lc_divider", shape('    <solid android:color="#1FFFFFFF" />'))

# Scrim behind sheets
add("lc_scrim", shape('    <solid android:color="#B306070F" />'))

# Vignette to sink the screen edges into the void
add("lc_vignette", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:type="radial"
        android:gradientRadius="420dp"
        android:startColor="#0006070F"
        android:centerColor="#3306070F"
        android:endColor="#B306070F" />
</shape>""")

# ══════════════════════════════════════════════════════════════════════════════
# 2. ORBS & LIQUID LIGHT
# ══════════════════════════════════════════════════════════════════════════════

# Idle glass orb (dock / toolbar)
add("lc_orb_glass", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <gradient
                android:type="linear"
                android:angle="270"
                android:startColor="#21FFFFFF"
                android:endColor="#0FFFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>""")

# Liquid core (active orb) — cyan -> violet blob
add("lc_orb_core", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <gradient
                android:type="radial"
                android:gradientRadius="40dp"
                android:startColor="#F235E6FF"
                android:centerColor="#C08B6CFF"
                android:endColor="#66FF4FD8" />
        </shape>
    </item>
    <item android:left="5dp" android:top="4dp" android:right="11dp" android:bottom="15dp">
        <shape android:shape="oval">
            <solid android:color="#99FFFFFF" />
        </shape>
    </item>
</layer-list>""")

# Soft outward glow (place behind active orb)
add("lc_orb_glow", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <gradient
        android:type="radial"
        android:gradientRadius="42dp"
        android:startColor="#6635E6FF"
        android:centerColor="#338B6CFF"
        android:endColor="#00FF4FD8" />
</shape>""")

# Active orb ring
add("lc_orb_active", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <gradient
                android:type="linear"
                android:angle="270"
                android:startColor="#2E35E6FF"
                android:endColor="#2E8B6CFF" />
            <stroke android:width="1.5dp" android:color="@color/lc_cyan" />
        </shape>
    </item>
</layer-list>""")

# Launch orb — the big liquid button
add("lc_launch_orb", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- outer glass shell -->
    <item>
        <shape android:shape="oval">
            <gradient
                android:type="linear"
                android:angle="270"
                android:startColor="#33FFFFFF"
                android:endColor="#12FFFFFF" />
            <stroke android:width="2dp" android:color="@color/lc_rim_strong" />
        </shape>
    </item>
    <!-- liquid core -->
    <item android:left="16dp" android:top="16dp" android:right="16dp" android:bottom="16dp">
        <shape android:shape="oval">
            <gradient
                android:type="radial"
                android:gradientRadius="80dp"
                android:startColor="#FFB6F7FF"
                android:centerColor="#E635E6FF"
                android:endColor="#FF8B6CFF" />
        </shape>
    </item>
    <!-- inner shadow ring for depth -->
    <item android:left="16dp" android:top="16dp" android:right="16dp" android:bottom="16dp">
        <shape android:shape="oval">
            <stroke android:width="1dp" android:color="#3F06070F" />
        </shape>
    </item>
</layer-list>""")

# Halo track + progress halo for the launch orb
add("lc_halo_track", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <stroke android:width="3dp" android:color="#1FFFFFFF" />
</shape>""")

add("lc_halo_glow", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <stroke android:width="3dp" android:color="#9935E6FF" />
</shape>""")

# Player stage glow + pedestal
add("lc_stage_glow", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <gradient
        android:type="radial"
        android:gradientRadius="70dp"
        android:startColor="#5535E6FF"
        android:centerColor="#338B6CFF"
        android:endColor="#0006070F" />
</shape>""")

add("lc_pedestal_ring", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <gradient
        android:type="linear"
        android:angle="0"
        android:startColor="#0035E6FF"
        android:centerColor="#AA35E6FF"
        android:endColor="#00FF4FD8" />
</shape>""")

# Slow drifting shimmer (GPU-composited only)
add("lc_shimmer", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="0"
        android:startColor="#00FFFFFF"
        android:centerColor="#0FFFFFFF"
        android:endColor="#00FFFFFF" />
</shape>""")

# ══════════════════════════════════════════════════════════════════════════════
# 3. CHIPS, BADGES, BUTTONS
# ══════════════════════════════════════════════════════════════════════════════

add("lc_chip", glass_solid("#12FFFFFF", "999dp", RIM_1))
add("lc_chip_active", glass_solid(CYAN_A, "999dp", "#8035E6FF", "1dp"))
add("lc_badge_local", glass_solid("#1FFFFFFF", "999dp", RIM_1))
add("lc_badge_official", glass_solid("#33E8C56A", "8dp", "#8CE8C56A"))
add("lc_badge_mint", glass_solid(MINT_A, "999dp", "#667CFFCB"))
add("lc_badge_amber", glass_solid("#33E8C56A", "999dp", "#66E8C56A"))
add("lc_badge_red", glass_solid("#33FF6B6B", "999dp", "#66FF6B6B"))
add("lc_badge_cyan", glass_solid(CYAN_A, "999dp", "#6635E6FF"))

# Primary liquid action slab (Install / Save & apply)
add("lc_btn_liquid", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient
                android:type="linear"
                android:angle="0"
                android:startColor="@color/lc_cyan"
                android:centerColor="@color/lc_violet"
                android:endColor="@color/lc_magenta" />
        </shape>
    </item>
</layer-list>""")

# Play (server / instance) green slab
add("lc_btn_play", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient
                android:type="linear"
                android:angle="270"
                android:startColor="#FF66FFA8"
                android:centerColor="@color/lc_green"
                android:endColor="#FF1FA84E" />
            <stroke android:width="1dp" android:color="#667CFFCB" />
        </shape>
    </item>
</layer-list>""")

# Neutral secondary pill
add("lc_btn_ghost", glass_solid("#14FFFFFF", "999dp", RIM_2))

# Glass action orb (round mini buttons)
add("lc_orb_button", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#40FFFFFF">
    <item android:drawable="@drawable/lc_orb_glass" />
</ripple>""")

add("lc_orb_button_active", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#66FFFFFF">
    <item android:drawable="@drawable/lc_orb_active" />
</ripple>""")

# Press feedback for large glass surfaces
add("lc_ripple_sheet", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#33FFFFFF">
    <item android:drawable="@drawable/lc_glass_card" />
</ripple>""")

add("lc_ripple_row", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#2EFFFFFF">
    <item android:drawable="@drawable/lc_glass_row" />
</ripple>""")

add("lc_ripple_capsule", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#33FFFFFF">
    <item android:drawable="@drawable/lc_glass_capsule" />
</ripple>""")

add("lc_ripple_ghost", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#33FFFFFF">
    <item android:drawable="@drawable/lc_btn_ghost" />
</ripple>""")

add("lc_ripple_tile", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#33FFFFFF">
    <item android:drawable="@drawable/lc_glass_row" />
</ripple>""")

# Status dots
add("lc_dot_mint", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/lc_mint" />
</shape>""")

add("lc_dot_amber", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/lc_gold" />
</shape>""")

add("lc_dot_red", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/lc_red" />
</shape>""")

# Sliders (glass track + liquid thumb)
add("lc_slider_track", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:gravity="center_vertical">
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <solid android:color="#1FFFFFFF" />
        </shape>
    </item>
</layer-list>""")

add("lc_slider_thumb", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <gradient
                android:type="radial"
                android:gradientRadius="70dp"
                android:startColor="#FFB6F7FF"
                android:endColor="@color/lc_cyan" />
            <stroke android:width="2dp" android:color="#66FFFFFF" />
        </shape>
    </item>
</layer-list>""")

# Horizontal progress (downloads)
add("lc_progress_horizontal", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <solid android:color="#1FFFFFFF" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="999dp" />
                <gradient
                    android:angle="0"
                    android:startColor="@color/lc_cyan"
                    android:endColor="@color/lc_violet" />
            </shape>
        </clip>
    </item>
</layer-list>""")

# Rounded-corner clip for hero images
add("lc_clip_round", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#00000000" />
    <corners android:radius="@dimen/lc_radius_card" />
</shape>""")

# ══════════════════════════════════════════════════════════════════════════════
# 4. VECTOR ICONS
# ══════════════════════════════════════════════════════════════════════════════

def vector(name, path, tint=None, size=24):
    tint_line = ('    android:tint="%s"\n' % tint) if tint else ""
    add(name, """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="%ddp"
    android:height="%ddp"
    android:viewportWidth="24"
    android:viewportHeight="24"
%s>
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="%s" />
</vector>""" % (size, size, tint_line, path))


vector("lc_ic_instances",
       "M11.99 18.54l-7.37-5.73L3 14.07l9 7 9-7-1.63-1.27-7.38 5.74zM12 16l7.36-5.73L21 9l-9-7-9 7 1.63 1.27L12 16z")
vector("lc_ic_mods",
       "M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z")
vector("lc_ic_servers",
       "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z")
vector("lc_ic_sliders",
       "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z")
vector("lc_ic_spark",
       "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z")
vector("lc_ic_signal",
       "M17 4h3v16h-3V4zM5 14h3v6H5v-6zm6-5h3v11h-3V9z")
vector("lc_ic_crown",
       "M5 16L3 5l5.5 5L12 4l3.5 6L21 5l-2 11H5zm14 3c0 .6-.4 1-1 1H6c-.6 0-1-.4-1-1v-1h14v1z")
vector("lc_ic_youtube",
       "M21.58 7.19c-.23-.86-.91-1.54-1.77-1.77C18.25 5 12 5 12 5s-6.25 0-7.81.42c-.86.23-1.54.91-1.77 1.77C2 8.75 2 12 2 12s0 3.25.42 4.81c.23.86.91 1.54 1.77 1.77C5.75 19 12 19 12 19s6.25 0 7.81-.42c.86-.23 1.54-.91 1.77-1.77C22 15.25 22 12 22 12s0-3.25-.42-4.81zM10 15V9l5.2 3-5.2 3z")
vector("lc_ic_globe_small",
       "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z")
vector("lc_ic_close",
       "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z")
vector("lc_ic_person",
       "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z")
vector("lc_ic_delete",
       "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z")
vector("lc_ic_check_circle",
       "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z")
vector("lc_ic_star",
       "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z")
vector("lc_ic_ms",
       "M2 2h9.5v9.5H2V2zm10.5 0H22v9.5h-9.5V2zM2 12.5h9.5V22H2v-9.5zm10.5 0H22V22h-9.5v-9.5z")
vector("lc_ic_elyby",
       "M5 4h14v3H8v3h9v3H8v4h11v3H5V4z")

vector("lc_ic_update",
       "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z")


def main():
    os.makedirs(DRAWABLE, exist_ok=True)
    written = 0
    for name, xml in FILES.items():
        path = os.path.join(DRAWABLE, name + ".xml")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(xml)
        written += 1
    print("wrote %d drawables into %s" % (written, DRAWABLE))


if __name__ == "__main__":
    main()
