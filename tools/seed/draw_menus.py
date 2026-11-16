"""按设计稿 14 的样式画示例菜单的“原照片”：深色桌面上一张微微倾斜的纸，竖排手写体。

只用 macOS 自带的 Klee 字体（手写风格）生成，不使用任何网上的照片。
用法：python3 tools/seed/draw_menus.py androidApp/app/src/main/assets/seed/seed.json androidApp/app/src/main/assets/seed/menus
"""
import glob
import json
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

KANJI = "〇一二三四五六七八九"
ROTATE = set("ー〜～")
TITLES = {
    "place-torimatsu": "本日のおすすめ",
    "place-minato": "お品書き",
    "place-hinata": "品書き",
    "place-komorebi": "喫茶めにゅう",
    "place-tsubame": "洋食めにゅう",
}


def find_font():
    hits = glob.glob("/System/Library/AssetsV2/com_apple_MobileAsset_Font*/*.asset/AssetData/Klee.ttc")
    if not hits:
        raise SystemExit("找不到 Klee 字体（macOS 字体册里下载 Klee 后再试）")
    return hits[0]


def kanji_price(n):
    return "".join(KANJI[int(c)] for c in str(n))


def draw_vertical(img, x, y, text, font, step, color):
    d = ImageDraw.Draw(img)
    for ch in text:
        if ch == "　":
            y += step
            continue
        if ch in ROTATE:
            tile = Image.new("RGBA", (step, step), (0, 0, 0, 0))
            ImageDraw.Draw(tile).text((step / 2, step / 2), ch, font=font, fill=color, anchor="mm")
            img.alpha_composite(tile.rotate(-90), (int(x - step / 2), int(y)))
        else:
            d.text((x, y + step / 2), ch, font=font, fill=color, anchor="mm")
        y += step


def page(font_path, title, lines, rnd):
    w, h = 760, 840
    bg = Image.new("RGBA", (w, h), (43, 50, 49, 255))
    noise = Image.effect_noise((w, h), 18).convert("L").point(lambda v: 90 if v > 128 else 0)
    bg = Image.composite(Image.new("RGBA", (w, h), (52, 60, 58, 255)), bg, noise)
    pw, ph = 584, 680
    paper = Image.new("RGBA", (pw, ph), (250, 247, 238, 255))
    pn = Image.effect_noise((pw, ph), 10).convert("L").point(lambda v: 70 if v > 140 else 0)
    paper = Image.composite(Image.new("RGBA", (pw, ph), (241, 236, 222, 255)), paper, pn)
    cols = [title] + lines
    margin_x, margin_y = 44, 48
    step = min(46, (ph - 2 * margin_y) // max(len(c) for c in cols))
    col_w = min(78, (pw - 2 * margin_x) // len(cols))
    title_font = ImageFont.truetype(font_path, int(min(step * 1.15, col_w * 0.8)))
    body_font = ImageFont.truetype(font_path, int(min(step * 0.92, col_w * 0.62)))
    ink = (34, 34, 38, 255)
    x = pw - margin_x - col_w / 2
    for i, text in enumerate(cols):
        font = title_font if i == 0 else body_font
        s = int(step * 1.15) if i == 0 else step
        draw_vertical(paper, x + rnd.uniform(-2, 2), margin_y + rnd.uniform(0, 6), text, font, s, ink)
        x -= col_w * (1.25 if i == 0 else 1)
    shadow = Image.new("RGBA", (pw + 40, ph + 40), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle((20, 24, pw + 20, ph + 24), fill=(0, 0, 0, 120))
    shadow = shadow.filter(ImageFilter.GaussianBlur(12)).rotate(2, expand=True)
    rotated = paper.rotate(2, expand=True, resample=Image.BICUBIC)
    ox, oy = (w - rotated.width) // 2, (h - rotated.height) // 2
    bg.alpha_composite(shadow, (ox - 20, oy - 16))
    bg.alpha_composite(rotated, (ox, oy))
    return bg.convert("RGB")


def main():
    seed_path, out_dir = sys.argv[1], sys.argv[2]
    font_path = find_font()
    seed = json.load(open(seed_path, encoding="utf-8"))
    os.makedirs(out_dir, exist_ok=True)
    for place in seed["places"]:
        for scan in place.get("scans", []):
            dishes = [d for d in place["dishes"] if d.get("scanId") == scan["id"]]
            lines = [f'{d["nameOriginal"]}　{kanji_price(d["priceMinor"])}' for d in dishes]
            img = page(font_path, TITLES.get(place["id"], "お品書き"), lines, random.Random(scan["id"]))
            name = os.path.basename(scan["photoPath"])
            img.save(os.path.join(out_dir, name), "JPEG", quality=78, optimize=True, progressive=True)
            print(name, len(dishes))


if __name__ == "__main__":
    main()
