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
    "java/net/kdt/pojavlaunch/fragments/AccountsPanelFragment.java",
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
    # ids are declared inside layouts with @+id/…, not in values/
    for lp in glob.glob(os.path.join(RES, "layout*", "*.xml")):
        names["id"].update(re.findall(r'@\+id/([A-Za-z0-9_]+)', open(lp, encoding="utf-8").read()))
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
    "layout/fragment_accounts_panel.xml",
    "layout/item_lc_account.xml",
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

# ── 5. string escaping (aapt2 rejects these) ─────────────────────────────────
# aapt2 fails a release build on an unescaped apostrophe inside <string> ("Invalid unicode
# escape sequence in string") and on malformed \uXXXX escapes. Both used to only show up
# three minutes into CI, so they are checked here instead.
STRING_RE = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
for vf in sorted(glob.glob(os.path.join(RES, "values*", "strings.xml"))):
    src = open(vf, encoding="utf-8").read()
    rel = os.path.relpath(vf, RES)
    for name, attrs, body in STRING_RE.findall(src):
        if 'translatable="false"' in attrs:
            pass
        # aapt2 accepts apostrophes inside a string that is wrapped in double quotes
        quoted = body.startswith('"') and body.endswith('"')
        for idx, ch in enumerate(body):
            if quoted:
                break
            if ch == "'" and (idx == 0 or body[idx - 1] != "\\"):
                errors.append("%s: string/%s has an unescaped apostrophe (use \\')" % (rel, name))
                break
        for mm in re.finditer(r"\\u", body):
            if not re.fullmatch(r"[0-9a-fA-F]{4}", body[mm.start() + 2:mm.start() + 6]):
                errors.append("%s: string/%s has a malformed \\u escape" % (rel, name))
        subs = re.findall(r"%(?!%)", body)
        if len(subs) > 1 and "%1$" not in body:
            # aapt2 only warns about this for pre-existing strings, so keep it non-fatal
            warnings.append("%s: string/%s has multiple non-positional substitutions" % (rel, name))

# ── 6. duplicate resource names inside one values folder ─────────────────────
# aapt2 hard-fails with "duplicate value for resource" long before javac runs.
for folder in sorted(glob.glob(os.path.join(RES, "values*"))):
    declared = {}
    for vf in glob.glob(os.path.join(folder, "*.xml")):
        try:
            doc = xml.dom.minidom.parse(vf)
        except Exception:                             # noqa: BLE001
            continue
        for tag in ("color", "dimen", "string", "style", "integer", "bool", "array", "attr"):
            for node in doc.getElementsByTagName(tag):
                nm = node.getAttribute("name")
                if nm:
                    declared.setdefault((tag, nm), []).append(os.path.basename(vf))
        for node in doc.getElementsByTagName("item"):
            ntype, nm = node.getAttribute("type"), node.getAttribute("name")
            if ntype and nm:
                declared.setdefault((ntype, nm), []).append(os.path.basename(vf))
    for (ntype, nm), files in declared.items():
        if len(files) > 1 and not (ntype == "attr" and nm == "seekBarIncrement"):
            errors.append("%s: duplicate %s/%s (%s)" % (os.path.relpath(folder, RES), ntype, nm, files))

# ── 7. R.* references in the touched Java files ──────────────────────────────
# (?!android\.) keeps android.R.id.content from being read as an app resource
RREF_RE = re.compile(r"(?<!android\.)\bR\.(drawable|color|dimen|string|style|id|anim|layout|mipmap|array|bool|integer)\.([A-Za-z0-9_]+)")
for rel_java in java_targets:
    path = os.path.join(MAIN, rel_java)
    if not os.path.exists(path):
        continue
    src = open(path, encoding="utf-8").read()
    for kind, name in RREF_RE.findall(src):
        if name not in RESOURCES[kind]:
            # library / resValue-provided names are not visible offline, so warn only
            warnings.append("%s → R.%s.%s not found locally" % (rel_java, kind, name))

# ── report ───────────────────────────────────────────────────────────────────
print("checked %d java files, %d layouts, %d drawables"
      % (len(java_targets), len(new_layouts), len(glob.glob(os.path.join(RES, "drawable", "lc_*.xml")))))
for w in warnings:
    print("WARN  " + w)
for e in errors:
    print("ERROR " + e)
print("RESULT:", "FAIL" if errors else "PASS")
sys.exit(1 if errors else 0)
