#!/usr/bin/env python3
"""Scrape Bongard problems from foundalis.com into Android app assets.

Outputs:
  app/src/main/assets/img/pNNN.gif   - problem images
  app/src/main/assets/problems.json  - [{num, image, designer, left, right}]
"""
import json
import re
import sys
import time
import urllib.request
from html import unescape
from pathlib import Path

BASE = "https://www.foundalis.com/res/bps/"
ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
IMG_DIR = ASSETS / "img"
CACHE = Path(__file__).resolve().parent / "cache"

UA = {"User-Agent": "Mozilla/5.0 (personal Bongard study app scraper; one-time fetch)"}


def fetch(url, binary=False, retries=3):
    """Fetch a URL with on-disk caching so re-runs don't hammer the site."""
    key = re.sub(r"[^A-Za-z0-9._-]", "_", url.replace(BASE, ""))
    cached = CACHE / key
    if cached.exists():
        data = cached.read_bytes()
    else:
        for attempt in range(retries):
            try:
                with urllib.request.urlopen(
                    urllib.request.Request(url, headers=UA), timeout=30
                ) as r:
                    data = r.read()
                break
            except Exception as e:
                if attempt == retries - 1:
                    raise
                print(f"  retry {url}: {e}", flush=True)
                time.sleep(2 * (attempt + 1))
        CACHE.mkdir(parents=True, exist_ok=True)
        cached.write_bytes(data)
        time.sleep(0.4)  # be polite to the server
    return data if binary else data.decode("utf-8", errors="replace")


def clean_text(html_fragment):
    text = re.sub(r"<[^>]+>", " ", html_fragment)
    text = unescape(text)
    text = text.replace("’", "'").replace("‘", "'")
    return re.sub(r"\s+", " ", text).strip()


def parse_index():
    html = fetch(BASE + "bpidx.htm")
    hrefs = re.findall(r'href="([a-z]+/p(\d+)\.htm)"', html)
    by_num = {}
    for href, num_s in hrefs:
        num = int(num_s)
        # A few numbers appear twice in the index (stale links); keep the
        # last occurrence, which is the one in the designer's own section.
        by_num[num] = href
    return dict(sorted(by_num.items()))


def parse_solutions():
    html = fetch(BASE + "bongard_problems_solutions.htm")
    sols = {}
    # Rows: link to pNNN.htm ... then two <td> cells with left/right rules.
    for m in re.finditer(
        r'href="[a-z]+/p(\d+)\.htm"[^>]*>.*?</td>\s*'
        r'<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>',
        html,
        re.S,
    ):
        num = int(m.group(1))
        left, right = clean_text(m.group(2)), clean_text(m.group(3))
        if num in sols:
            # Alternative solution row: append.
            pl, pr = sols[num]
            sols[num] = (f"{pl} | alt: {left}", f"{pr} | alt: {right}")
        else:
            sols[num] = (left, right)
    return sols


def main():
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    index = parse_index()
    sols = parse_solutions()
    print(f"index: {len(index)} problems, solutions: {len(sols)}", flush=True)

    problems = []
    missing_sol = []
    for i, (num, href) in enumerate(index.items()):
        page = fetch(BASE + href)
        m = re.search(r"<h1>(.*?)</h1>", page, re.S)
        h1 = clean_text(m.group(1)) if m else f"BP#{num}."
        dm = re.search(r"Designer:\s*(.*)", h1)
        designer = dm.group(1).strip() if dm else "Unknown"

        im = re.search(r'<img\s+src="(p\d+\.\w+)"', page)
        if not im:
            print(f"  !! no image on {href}", flush=True)
            continue
        img_name = im.group(1)
        img_url = BASE + href.rsplit("/", 1)[0] + "/" + img_name
        out_name = f"p{num:03d}" + Path(img_name).suffix
        (IMG_DIR / out_name).write_bytes(fetch(img_url, binary=True))

        left, right = sols.get(num, ("", ""))
        if num not in sols:
            missing_sol.append(num)
        problems.append(
            {
                "num": num,
                "image": f"img/{out_name}",
                "designer": designer,
                "left": left,
                "right": right,
            }
        )
        if (i + 1) % 25 == 0:
            print(f"  {i + 1}/{len(index)} done", flush=True)

    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "problems.json").write_text(
        json.dumps(problems, indent=1, ensure_ascii=False)
    )
    print(f"wrote {len(problems)} problems", flush=True)
    if missing_sol:
        print(f"no solution for: {missing_sol}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
