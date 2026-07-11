#!/usr/bin/env python3
"""Generate docs/problems.json for the web viewer.

Unlike the Android assets, the web JSON references images by their path on
foundalis.com (relative to BASE) instead of bundling them, so the viewer
hotlinks the originals — nothing of Foundalis' is redistributed. Runs
entirely from scraper/cache/ if you've already run scrape.py.
"""
import json
import re
from pathlib import Path

from scrape import BASE, ROOT, fetch, parse_index, parse_solutions

P300_BOXES = [f"foundal/p300_{p}{l}.gif" for p in "12" for l in "ABCDEF"]


def main():
    index = parse_index()
    sols = parse_solutions()
    problems = []
    for num, href in index.items():
        page = fetch(BASE + href)
        m = re.search(r"<h1>(.*?)</h1>", page, re.S)
        h1 = re.sub(r"<[^>]+>", " ", m.group(1)) if m else ""
        dm = re.search(r"Designer:\s*(.*)", re.sub(r"\s+", " ", h1))
        designer = dm.group(1).strip() if dm else "Unknown"

        left, right = sols.get(num, ("", ""))
        entry = {"num": num, "page": href, "designer": designer,
                 "left": left, "right": right}
        if num == 300:
            # Served as 12 separate animated GIFs (the puzzle is about motion).
            entry["boxes"] = P300_BOXES
        else:
            im = re.search(r'<img\s+src="(p\d+\.\w+)"', page)
            if not im:
                print(f"!! no image on {href}")
                continue
            entry["image"] = href.rsplit("/", 1)[0] + "/" + im.group(1)
        problems.append(entry)

    out = ROOT / "docs" / "problems.json"
    out.write_text(json.dumps(problems, ensure_ascii=False, indent=1))
    print(f"wrote {len(problems)} problems to {out}")


if __name__ == "__main__":
    main()
