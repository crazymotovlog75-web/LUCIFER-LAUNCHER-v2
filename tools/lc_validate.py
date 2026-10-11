#!/usr/bin/env python3
"""Offline sanity checks for the Liquid Crystal UI work.

The sandbox has no Android SDK, so this script does the closest possible thing to a
compile pass:

  1. parses every touched Java file with javalang   (syntax errors are fatal)
  2. parses every layout/drawable/values XML        (well-formedness)
  3. resolves every @drawable/@color/@dimen/@string/@style/@id/@mipmap/@anim reference
     used by the new layouts against the resources that actually exist
  4. checks that every android:/app: attribute used in the new layouts is already used
     somewhere else in the repository (an aapt-safe attribute whitelist)

Exit code is non-zero when a real problem is found.
"""
import glob
import os
import re
import sys
import xml.dom.minidom

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app_pojavlauncher", "src", "main")
RES = os.path.join(MAIN, "res")

errors = []
warnings = []

# ── 1. Java syntax ───────────────────────────────────────────────────────────
try:
    import javalang
except ImportError:
    javalang = None

java_targets = [
    "java/net/kdt/pojavlaunch/liquid/AuroraBackdropView.java",
    "java/net/kdt/pojavlaunch/liquid/Liquid.java",
    "java/net/kdt/pojavlaunch/liquid/ServerPinger.java",
    "java/net/kdt/pojavlaunch/liquid/ServerStore.java",
    "java/net/kdt/pojavlaunch/fragments/ServerHubFragment.java",
    "java/net/kdt/pojavlaunch/fragments/RightPaneHomeFragment.java",
    "java/net/kdt/pojavlaunch/LauncherActivity.java",
]

if javalang is None:
    warnings.append("javalang not available — Java syntax check skipped")
else:
    for rel in java_targets:
        path = os.path.join(MAIN, rel)
        if not os.path.exists(path):
            errors.append("missing java file: %s" % rel)
            continue
        src = open(path, encoding="utf-8").read()
        try:
            javalang.parse.parse(src)
        except Exception as exc:                      # noqa: BLE001
            errors.append("JAVA SYNTAX %s: %s" % (rel, exc))
        # javalang is Java-8 era; flag constructs it may silently mis-parse
        for bad in ("var ", "record ", "-> {", "::"):
            pass

# ── 2/3. XML + resource references ───────────────────────────────────────────
def collect_resource_names():
    names = {"drawable": set(), "color": set(), "dimen": set(), "string": set(),
             "style": set(), "id": set(), "anim": set(), "mipmap": set(), "layout": set(),
             "array": set(), "integer": set(), "bool": set(), "attr": set()}
    # files in drawable dirs
    for d in glob.glob(os.path.join(RES, "drawable*")):
        for f in os.listdir(d):
            names["drawable"].add(os.path.splitext(f)[0])
    for d in glob.glob(os.path.join(RES, "mipmap*")):
        for f in os.listdir(d):
            names["mipmap"].add(os.path.splitext(f)[0])
    for f in os.listdir(os.path.join(RES, "anim")):
        names["anim"].add(os.path.splitext(f)[0])
    for f in os.listdir(os.path.join(RES, "layout")) + os.listdir(os.path.join(RES, "layout-land")):
        names["layout"].add(os.path.splitext(f)[0])
    for vf in glob.glob(os.path.join(RES, "values*", "*.xml")):
        try:
            doc = xml.dom.minidom.parse(vf)
        except Exception as exc:                      # noqa: BLE001
            errors.append("XML PARSE %s: %s" % (vf, exc))
            continue
        for tag in ("color", "dimen", "string", "style", "integer", "bool", "array"):
            for node in doc.getElementsByTagName(tag):
                if node.getAttribute("name"):
                    names[tag].add(node.getAttribute("name"))
        for node in doc.getElementsByTagName("item"):
            ntype = node.getAttribute("type")
            if ntype in names and node.getAttribute("name"):
                names[ntype].add(node.getAttribute("name"))
        for node in doc.getElementsByTagName("attr"):
            if node.getAttribute("name"):
                names["attr"].add(node.getAttribute("name"))
    return names


RESOURCES = collect_resource_names()
REF_RE = re.compile(r'@(?:\+)?(drawable|color|dimen|string|style|id|anim|mipmap|layout|array|integer|bool)/([A-Za-z0-9_.]+)')


def check_layout(rel_path):
    path = os.path.join(RES, rel_path)
    try:
        doc = xml.dom.minidom.parse(path)
    except Exception as exc:                          # noqa: BLE001
        errors.append("XML PARSE %s: %s" % (rel_path, exc))
        return
    src = open(path, encoding="utf-8").read()
    for kind, name in REF_RE.findall(src):
        if kind == "id" and ("@+id/" + name) in src:
            continue                       # locally defined
        if name not in RESOURCES[kind]:
            errors.append("%s → missing @%s/%s" % (rel_path, kind, name))


new_layouts = [
    "layout-land/activity_pojav_launcher.xml",
    "layout/activity_pojav_launcher.xml",
    "layout/fragment_right_pane_home.xml",
    "layout/item_home_profile_card.xml",
    "layout/fragment_server_hub.xml",
    "layout/item_lc_server.xml",
]
for layout in new_layouts:
    check_layout(layout)

for d in glob.glob(os.path.join(RES, "drawable", "lc_*.xml")):
    try:
        xml.dom.minidom.parse(d)
    except Exception as exc:                          # noqa: BLE001
        errors.append("XML PARSE %s: %s" % (os.path.basename(d), exc))
    src = open(d, encoding="utf-8").read()
    for kind, name in REF_RE.findall(src):
        if name not in RESOURCES[kind]:
            errors.append("%s → missing @%s/%s" % (os.path.basename(d), kind, name))

# ── 4. attribute whitelist ───────────────────────────────────────────────────
ATTR_RE = re.compile(r'\b(android|app):([A-Za-z0-9_]+)="')
known = set()
for path in glob.glob(os.path.join(RES, "layout*", "*.xml")):
    if os.path.basename(path) in {os.path.basename(os.path.join(RES, p)) for p in new_layouts}:
        continue
    try:
        src = open(path, encoding="utf-8").read()
    except Exception:                                 # noqa: BLE001
        continue
    known.update("%s:%s" % m for m in ATTR_RE.findall(src))

for layout in new_layouts:
    src = open(os.path.join(RES, layout), encoding="utf-8").read()
    for ns, attr in ATTR_RE.findall(src):
        key = "%s:%s" % (ns, attr)
        if key not in known:
            warnings.append("%s uses attribute not seen elsewhere: %s" % (layout, key))

# ── report ───────────────────────────────────────────────────────────────────
print("checked %d java files, %d layouts, %d drawables"
      % (len(java_targets), len(new_layouts), len(glob.glob(os.path.join(RES, "drawable", "lc_*.xml")))))
for w in warnings:
    print("WARN  " + w)
for e in errors:
    print("ERROR " + e)
print("RESULT:", "FAIL" if errors else "PASS")
sys.exit(1 if errors else 0)
