#!/usr/bin/env python3
"""Re-skins the pre-existing shared drawables + palette with Liquid Crystal styling.

The launcher has ~60 screens sharing a small set of background drawables and colours, so
restyling these in one place gives the whole app the frosted-glass look while leaving every
layout, id and behaviour untouched.
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app_pojavlauncher", "src", "main", "res")
DRAWABLE = os.path.join(RES, "drawable")

F = {}

# ── reusable snippets ────────────────────────────────────────────────────────
def shape(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
            + body + "</shape>\n")


def rounded_shape(fill, radius, stroke=None):
    body = '    <solid android:color="%s" />\n    <corners android:radius="%s" />\n' % (fill, radius)
    if stroke:
        body += '    <stroke android:width="1dp" android:color="%s" />' % stroke
    return shape(body)


def oval(fill, stroke=None):
    body = '    <solid android:color="%s" />' % fill
    if stroke:
        body += '\n    <stroke android:width="1dp" android:color="%s" />' % stroke
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">\n'
            + body + "</shape>\n")


GLASS = """<?xml version="1.0" encoding="utf-8"?>
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
</layer-list>
"""

GLASS_SOFT = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <corners android:radius="@dimen/lc_radius_row" />
            <solid android:color="#12FFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>
"""

CAPSULE = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient android:angle="270" android:startColor="#1AFFFFFF" android:endColor="#0DFFFFFF" />
            <stroke android:width="1dp" android:color="@color/lc_rim" />
        </shape>
    </item>
</layer-list>
"""

LIQUID_BTN = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient android:angle="0" android:startColor="@color/lc_cyan"
                android:centerColor="@color/lc_violet" android:endColor="@color/lc_magenta" />
        </shape>
    </item>
</layer-list>
"""

PLAY_BTN = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="999dp" />
            <gradient android:angle="270" android:startColor="#FF66FFA8"
                android:centerColor="@color/lc_green" android:endColor="#FF1FA84E" />
            <stroke android:width="1dp" android:color="#667CFFCB" />
        </shape>
    </item>
</layer-list>
"""

DASHED = """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#0A35E6FF" />
    <corners android:radius="@dimen/lc_radius_card" />
    <stroke android:width="1.5dp" android:color="#7035E6FF" android:dashWidth="7dp" android:dashGap="5dp" />
</shape>
"""

def add(name, xml):
    F[name] = xml

# cards / panels
for name in ["background_card", "bg_fastclient_card", "bg_profile_card", "bg_section_card",
             "bg_fabric_section_card", "bg_install_card", "bg_download_card", "bg_preference_card",
             "bg_canvas_card", "bg_right_pane", "bg_glass_card", "bg_active_glass_card",
             "bg_preview_card", "bg_fabric_info_card", "bg_fabric_header_card", "bg_fabric_version_item",
             "bg_install_screen_gradient", "bg_install_primary_button"]:
    add(name, GLASS)

# rows / soft surfaces
for name in ["bg_folder_row", "bg_chip_dark", "bg_label_chip", "bg_preview_box", "bg_cursor_preview",
             "bg_empty_state", "bg_selected_version_badge", "bg_loader_badge", "bg_nav_item_active",
             "bg_download_progress_bar", "bg_progress_track", "bg_dialog_fastclient",
             "bg_fabric_version_item", "bg_fabric_popup", "bg_fabric_error_strip"]:
    add(name, GLASS_SOFT)

# capsules / pills / badges
for name in ["bg_badge_pill", "bg_badge_online", "bg_green_pill", "bg_client_features_btn",
             "bg_client_features_btn_filled", "bg_account_spinner", "bg_settings_btn",
             "bg_header_bar", "bg_bottom_bar", "bg_bottom_panel", "bg_save_bar",
             "bg_icon_badge_neon", "bg_rank_badge_gold", "bg_rank_badge_purple",
             "bg_rank_badge_blue", "bg_rank_badge_pink", "bg_fabric_stable_badge"]:
    add(name, CAPSULE)

# action slabs
for name in ["bg_btn_play_orange", "bg_play_button_green", "bg_green_gradient_button",
             "bg_neon_button", "bg_install_button_fabric", "bg_install_button_primary",
             "bg_install_button_primary", "bg_fabric_install_button", "bg_save_button",
             "bg_save_button_neon", "bg_fabric_retry_button"]:
    add(name, PLAY_BTN if "play" in name or "green" in name else LIQUID_BTN)

add("bg_dashed_border", DASHED)
add("bg_fab_create_profile", DASHED)

# small decorative pieces
add("bg_dot_online", oval("@color/lc_mint"))
add("bg_dot_online_lc", oval("@color/lc_mint"))
add("bg_neon_dot", oval("@color/lc_cyan"))
add("bg_neon_dot_small", oval("@color/lc_cyan"))
add("bg_neon_indicator", rounded_shape("@color/lc_cyan", "999dp"))
add("bg_neon_indicator_small", rounded_shape("@color/lc_cyan", "999dp"))
add("bg_circle", oval("#2635E6FF", "#6635E6FF"))
add("bg_color_circle", oval("#2635E6FF", "#6635E6FF"))
add("background_rounded_icon", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("background_icon_edit", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("background_icon_edit_new", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("background_image_rounded", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("bg_profile_icon_rounded", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("bg_profile_icon_glow", oval("#3335E6FF"))
add("bg_icon_glow", oval("#3335E6FF"))
add("bg_player_head", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("bg_server_icon", rounded_shape("#16FFFFFF", "@dimen/lc_radius_tile", "@color/lc_rim"))
add("bg_server_card", GLASS)
add("bg_icon_neon", oval("#2635E6FF", "#6635E6FF"))
add("bg_icon_button_neon", oval("#2635E6FF", "#6635E6FF"))
add("bg_icon_button_round", oval("#1AFFFFFF", "#40FFFFFF"))
add("bg_icon_container", rounded_shape("#12FFFFFF", "@dimen/lc_radius_row", "@color/lc_rim"))
add("bg_color_black", rounded_shape("#CC06070F", "@dimen/lc_radius_card", "@color/lc_rim"))
add("background_overlay", rounded_shape("#6606070F", "@dimen/lc_radius_card"))
add("bg_backdrop", """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <gradient android:angle="270" android:startColor="@color/lc_void_soft"
                android:centerColor="@color/lc_void" android:endColor="#FF04050C" />
        </shape>
    </item>
</layer-list>
""")

# gradients used as full-screen backgrounds
for name in ["bg_gradient_default", "bg_gradient_amethyst", "bg_gradient_arctic",
             "bg_gradient_crimson", "bg_gradient_forest_green", "bg_gradient_midnight_blue"]:
    add(name, """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <gradient android:angle="270" android:startColor="#FF0B0D1E"
                android:centerColor="@color/lc_void" android:endColor="#FF04050C" />
        </shape>
    </item>
</layer-list>
""")


def main():
    written = 0
    for name, xml in F.items():
        with open(os.path.join(DRAWABLE, name + ".xml"), "w", encoding="utf-8") as fh:
            fh.write(xml)
        written += 1
    print("re-skinned %d existing drawables" % written)


if __name__ == "__main__":
    main()
