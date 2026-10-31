import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from import_bookmarks import convert, merge_preferences, page_for


class BookmarkImportTest(unittest.TestCase):
    def test_nested_folders_and_exact_duplicates(self):
        source = '''<DL><DT><H3>Artists</H3><DL><DT><H3>category</H3><DL>
        <DT><A HREF="https://hitomi.la/artist/example%20artist-korean-1.html">Example &amp; Artist</A>
        </DL></DL><DT><H3>히토미1</H3><DL>
        <DT><A HREF="https://hitomi.la/reader/123.html#1">Example 1</A>
        <DT><A HREF="https://hitomi.la/manga/example-123.html#9">Same ID</A>
        <DT><A HREF="https://hitomi.la/manga/example-124.html">Example 2</A>
        </DL><DT><H3>unrelated</H3><DL>
        <DT><A HREF="https://example.invalid/">Ignore</A></DL></DL>'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bookmarks.html"
            path.write_text(source, encoding="utf-8")
            imported, report = convert([path, path])
        self.assertEqual(report["selected"], 8)
        self.assertEqual(report["unique"], 3)
        self.assertEqual(len(report["duplicates"]), 5)
        self.assertEqual(report["unsupported"], [])
        self.assertEqual([g["name"] for g in imported["groups"]], ["작가들", "히토미1"])
        page = imported["groups"][0]["archivedTabs"][0]["history"][0]
        self.assertEqual(page["query"], "artist:example_artist")
        self.assertEqual(page["preferredLanguages"], ["korean"])
        group = imported["groups"][1]
        self.assertEqual(len(group["archivedTabs"]), 2)  # Similar titles stay.
        raw = b'<map><boolean name="keep" value="true" /></map>'
        merged, added = merge_preferences(raw, imported)
        self.assertEqual(added, 3)
        root = ET.fromstring(merged)
        self.assertEqual(root.find("boolean").attrib, {"name": "keep", "value": "true"})
        workspace = json.loads(root.find("string").text)
        self.assertEqual(len(workspace["tabs"]), 1)
        self.assertEqual(workspace["activeTabId"], workspace["tabs"][0]["id"])
        again, added = merge_preferences(merged, imported)
        self.assertEqual(added, 0)
        self.assertEqual(ET.fromstring(again).find("string").text, root.find("string").text)

    def test_legacy_workspace_preserved(self):
        workspace = {"version": 2, "tabs": [{"id": "existing", "history": [
            {"type": "search", "query": "artist:example", "preferredLanguages": []}], "currentIndex": 0}],
            "activeTabId": "existing", "groups": [], "overviewOrder": ["tab:existing"]}
        root = ET.Element("map")
        ET.SubElement(root, "string", name="browser_workspace").text = json.dumps(workspace)
        merged, added = merge_preferences(ET.tostring(root), {"groups": []})
        self.assertEqual(added, 0)
        new = ET.fromstring(merged)
        self.assertEqual(json.loads(new.find("./string[@name='search_workspace']").text), workspace)
        self.assertEqual(new.find("./string[@name='browser_workspace']").text, json.dumps(workspace))

    def test_search_fragment_and_tag_namespace(self):
        def page(url):
            return page_for({"url": url, "title": "Synthetic"})
        self.assertEqual(page("https://hitomi.la/search.html#artist%3Aexample")['query'], 'artist:example')
        self.assertEqual(page("https://hitomi.la/search.html?artist%3Aexample%20language%3Akorean")['query'], 'artist:example language:korean')
        self.assertEqual(page("https://hitomi.la/tag/female%3Aexample-all.html")['query'], 'female:example')
        self.assertEqual(page("https://hitomi.la/tag/example-korean.html")['query'], 'tag:example')
        for url in ("https://example.invalid/reader/123.html", "https://hitomi.la/unknown", "https://hitomi.la/search.html"):
            with self.assertRaises(ValueError):
                page(url)

    def test_flat_html_and_unsupported_report(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "flat.html"
            path.write_text('<a href="https://hitomi.la/reader/123.html">Synthetic</a>'
                            '<a href="https://example.invalid/">Unsupported</a>', encoding="utf-8")
            groups, report = convert([path], "작가들")
            self.assertEqual(len(groups["groups"]), 1)
            self.assertEqual(report["unique"], 1)
            self.assertEqual(len(report["unsupported"]), 1)


if __name__ == "__main__":
    unittest.main()
