#!/usr/bin/env python3
"""BP#300 is served as 12 separate box GIFs in an HTML table; composite them
into one standard-looking 516x330 board and insert it into problems.json."""
import io
import json

from PIL import Image

from scrape import ASSETS, BASE, IMG_DIR, fetch, parse_solutions

BOXES = ["1A", "1B", "1C", "1D", "1E", "1F", "2A", "2B", "2C", "2D", "2E", "2F"]

canvas = Image.new("RGB", (516, 330), "white")
for name in BOXES:
    data = fetch(BASE + f"foundal/p300_{name}.gif", binary=True)
    box = Image.open(io.BytesIO(data)).convert("RGB")
    page, letter = name[0], name[1]
    col, row = "ABCDEF".index(letter) % 2, "ABCDEF".index(letter) // 2
    x = (4 if page == "1" else 292) + col * 110
    y = 10 + row * 110
    # 1px border like the site's border=1 imgs
    bordered = Image.new("RGB", (102, 102), "black")
    bordered.paste(box.resize((100, 100)), (1, 1))
    canvas.paste(bordered, (x, y))

canvas.save(IMG_DIR / "p300.gif")

# The boxes are ANIMATED gifs (the puzzle is about motion), each looping on
# its own period — ship them individually so the app can animate the grid.
for name in BOXES:
    (IMG_DIR / f"p300_{name}.gif").write_bytes(
        fetch(BASE + f"foundal/p300_{name}.gif", binary=True)
    )

problems = json.loads((ASSETS / "problems.json").read_text())
problems = [p for p in problems if p["num"] != 300]
left, right = parse_solutions().get(300, ("", ""))
problems.append(
    {
        "num": 300,
        "image": "img/p300.gif",
        "boxes": [f"img/p300_{n}.gif" for n in BOXES],
        "designer": "H. E. Foundalis",
        "left": left,
        "right": right,
    }
)
problems.sort(key=lambda p: p["num"])
(ASSETS / "problems.json").write_text(json.dumps(problems, indent=1, ensure_ascii=False))
print(f"now {len(problems)} problems; BP#300 solution: {left!r} / {right!r}")
