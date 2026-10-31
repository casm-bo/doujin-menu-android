"""Convert Chrome HTML to saved Android groups; optionally merge via adb run-as.

Python 3.10+, standard library only. Keep output/backup files outside the repo.
Examples:
  python tools/import_bookmarks.py bookmarks.html --output /tmp/groups.json
  python tools/import_bookmarks.py cleaned.html --flat-group 작가들 --output /tmp/groups.json
  python tools/import_bookmarks.py bookmarks.html --output /tmp/groups.json --install

--install needs a debuggable app outside Secure Folder. It stops the app, backs
up its preferences, merges saved groups, verifies the write, and leaves it stopped.
"""

import argparse
import copy
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess
import uuid
from urllib.parse import parse_qs, unquote, urlsplit
import xml.etree.ElementTree as ET


FOLDERS = {"만화 - 작가", "만화-작가", "히토미1", "Artist", "Artists", "히토미2"}
PREFERENCES = "shared_prefs/browser_preferences.xml"


class Bookmarks(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.pending = None
        self.capture = None
        self.rows = []

    def handle_starttag(self, tag, attrs):
        if tag in ("h3", "a"):
            self.capture = [tag, "", dict(attrs)]
        elif tag == "dl":
            self.stack.append(self.pending)
            self.pending = None

    def handle_data(self, data):
        if self.capture is not None:
            self.capture[1] += data

    def handle_endtag(self, tag):
        if self.capture is not None and tag == self.capture[0]:
            _, title, attrs = self.capture
            if tag == "h3":
                self.pending = title.strip()
            else:
                self.rows.append({"folders": [f for f in self.stack if f],
                                  "title": title.strip(), "url": attrs.get("href", "")})
            self.capture = None
        elif tag == "dl" and self.stack:
            self.stack.pop()


def page_for(row):
    url = urlsplit(row["url"])
    if url.scheme not in ("http", "https") or url.hostname not in ("hitomi.la", "www.hitomi.la"):
        raise ValueError("Not a Hitomi URL")
    path = unquote(url.path)
    match = re.fullmatch(r"/(?:reader|galleries)/(\d+)\.html", path)
    if not match:
        match = re.fullmatch(r"/(?:doujinshi|manga|cg|game|anime|imageset)/.+-(\d+)\.html", path)
    if match:
        gallery_id = int(match[1])
        if gallery_id <= 0:
            raise ValueError("Invalid gallery ID")
        return {"type": "online", "galleryId": gallery_id, "title": row["title"]}
    match = re.fullmatch(r"/(artist|group|tag|series|character)/(.+)-([a-z]+)(?:-\d+)?\.html", path)
    languages = []
    if match:
        kind, value, language = match.groups()
        if language != "all":
            languages = [language]
        value = value.replace(" ", "_")
        query = value if kind == "tag" and value.startswith(("male:", "female:")) else f"{kind}:{value}"
    elif path == "/search.html":
        query = unquote(url.fragment).strip() or parse_qs(url.query).get("query", [""])[0].strip()
        if not query and url.query and "=" not in url.query:
            query = unquote(url.query).strip()
        if not query:
            raise ValueError("Empty search URL")
    elif re.fullmatch(r"/index-[a-z]+\.html", path):
        language = path[len("/index-"):-len(".html")]
        languages = [] if language == "all" else [language]
        query = ""
    else:
        raise ValueError("Unsupported Hitomi URL")
    return {"type": "search", "query": query, "submittedQuery": query,
            "preferredLanguages": languages}


def page_identity(page):
    if page["type"] == "online":
        return ("online", page["galleryId"])
    # Keep different search expressions, language selections and similar titles.
    return ("search", page["query"], tuple(sorted(page["preferredLanguages"])))


def convert(paths, flat_group=None):
    groups, seen, duplicates, unsupported = {}, {}, [], []
    selected = 0
    for path in paths:
        parser = Bookmarks()
        parser.feed(path.read_text(encoding="utf-8-sig"))
        for row in parser.rows:
            folder = next((f for f in row["folders"] if f in FOLDERS), None)
            if flat_group is None and folder is None:
                continue
            selected += 1
            row["sourceFile"] = str(path)
            name = flat_group or ("작가들" if folder in ("Artist", "Artists") else folder)
            try:
                page = page_for(row)
            except ValueError as error:
                unsupported.append({**row, "reason": str(error)})
                continue
            identity = page_identity(page)
            if identity in seen:
                duplicates.append({"kept": seen[identity], "removed": row})
                continue
            seen[identity] = row
            group = groups.setdefault(name, {"id": str(uuid.uuid4()), "name": name,
                                            "colorIndex": len(groups) % 6, "archivedTabs": []})
            page["key"] = str(uuid.uuid4())
            group["archivedTabs"].append({"id": str(uuid.uuid4()), "groupId": group["id"],
                                          "currentIndex": 0, "history": [page]})
    if selected == 0:
        raise ValueError("No matching bookmarks; use --flat-group for an already flattened HTML file")
    return {"version": 2, "groups": list(groups.values())}, {
        "selected": selected, "unique": len(seen), "duplicates": duplicates, "unsupported": unsupported}


def merge_preferences(raw, imported):
    root = ET.fromstring(raw)
    if root.tag != "map":
        raise ValueError("Invalid preferences root")
    entry = root.find("./string[@name='search_workspace']")
    legacy = root.find("./string[@name='browser_workspace']")
    source = entry if entry is not None else legacy
    workspace = json.loads(source.text) if source is not None else {
        "version": 2, "tabs": [{"id": str(uuid.uuid4()), "currentIndex": 0,
        "history": [{"type": "search", "key": str(uuid.uuid4()), "query": ""}]}],
        "groups": [], "overviewOrder": []}
    if not isinstance(workspace.get("tabs"), list) or not workspace["tabs"]:
        raise ValueError("Existing workspace has no valid tabs; refusing to replace it")
    workspace.setdefault("activeTabId", workspace["tabs"][0]["id"])
    groups = workspace.setdefault("groups", [])
    tabs = workspace["tabs"] + [t for g in groups for t in g.get("archivedTabs", [])]
    seen = set()
    for tab in tabs:
        history = tab["history"]
        page = history[tab.get("currentIndex", 0)]
        if page.get("type") in ("online", "search"):
            normalized = {**page, "preferredLanguages": page.get("preferredLanguages", [])}
            seen.add(page_identity(normalized))
    added = 0
    for original in imported["groups"]:
        group = copy.deepcopy(original)
        kept = []
        for tab in group["archivedTabs"]:
            identity = page_identity(tab["history"][0])
            if identity not in seen:
                seen.add(identity)
                kept.append(tab)
        if kept:
            group["archivedTabs"] = kept
            groups.append(group)
            added += len(kept)
    if entry is None:
        entry = ET.SubElement(root, "string", name="search_workspace")
    entry.text = json.dumps(workspace, ensure_ascii=False, separators=(",", ":"))
    return ET.tostring(root, encoding="utf-8", xml_declaration=True), added


def install(args, imported):
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.package):
        raise ValueError("Invalid package name")
    command = [args.adb] + (["-s", args.serial] if args.serial else [])

    def adb(*parts, data=None):
        result = subprocess.run(command + list(parts), input=data, capture_output=True, check=True)
        return result.stdout

    adb("shell", "run-as", args.package, "id")
    adb("shell", "am", "force-stop", args.package)
    adb("shell", "run-as", args.package, "sh", "-c", f"'test ! -e {PREFERENCES}.bak'")
    raw = adb("exec-out", "run-as", args.package, "cat", PREFERENCES)
    payload, added = merge_preferences(raw, imported)
    backup = args.output.with_name(args.output.stem + ".backup-" +
        datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8] + ".xml")
    with backup.open("xb") as stream:
        stream.write(raw)
    print(f"Backup: {backup}", flush=True)
    # Write as the app UID, in the same directory; rename only after a full write.
    temporary = PREFERENCES + ".bookmark-import"
    adb("shell", "run-as", args.package, "sh", "-c",
        f"'umask 077; cat > {temporary} && mv {temporary} {PREFERENCES}'", data=payload)
    if adb("exec-out", "run-as", args.package, "cat", PREFERENCES) != payload:
        raise RuntimeError(f"Read-back mismatch. App left stopped; restore backup: {backup}")
    print(f"Installed {added} tabs. Backup: {backup}. App left stopped; open it manually.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("html", type=Path, nargs="+")
    parser.add_argument("--output", type=Path, required=True, help="JSON path outside the repository")
    parser.add_argument("--flat-group", help="Use all links in cleaned HTML files as this one group")
    parser.add_argument("--install", action="store_true", help="Merge into a debuggable app via ADB")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", help="ADB target if multiple devices are connected")
    parser.add_argument("--package", default="com.doujinzip.android.debug")
    args = parser.parse_args()
    imported, report = convert(args.html, args.flat_group)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(imported, ensure_ascii=False, indent=2), encoding="utf-8")
    args.output.with_suffix(".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Selected {report['selected']}; unique {report['unique']}; duplicates {len(report['duplicates'])}; unsupported {len(report['unsupported'])}.")
    if report["unsupported"]:
        raise ValueError("Unsupported links listed in report; installation blocked to avoid omissions")
    if args.install:
        install(args, imported)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, RuntimeError) as error:
        raise SystemExit(str(error)) from error
