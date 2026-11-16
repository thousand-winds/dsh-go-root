#!/usr/bin/env python3
"""Builds the 白鲸 (whale) launcher icon from its master picture.

    python3 tool/make_launcher_icon_whale.py

The master is assets/brand/app-icon-whale.png (512 x 512): a dark whale on a
near-white card. One picture feeds every platform, the way the other four
launcher icons are put together:

    android/app/src/main/res/         legacy pngs, the adaptive layers
    ios/Runner/Assets.xcassets/       AppIconWhale.appiconset (1024, full bleed)
    ../ohos/AppScope/.../media/       the layered icon (1024 + 1024)
    lib/ui/settings/appearance/       whale_glyph.g.dart, the traced silhouette
                                      the picker previews with

The adaptive foreground is the whale alone, flat `GLYPH`, on the 108 grid at the
ink radius the brand glyph occupies (see GLYPH_RADIUS): the eye and the belly
stay holes, so the background shows through them as in the master. The
background is a vector gradient sampled from the card, on the same diagonal as
ic_launcher_background.xml. Needs Pillow and `potrace` (for the traced glyph).
"""
import re
import subprocess
import tempfile
from pathlib import Path
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw

APP = Path(__file__).resolve().parent.parent
ROOT = APP.parent
RES = APP / 'android/app/src/main/res'
MASTER = APP / 'assets/brand/app-icon-whale.png'
IMMUTABLE = APP / 'ios/Runner/Assets.xcassets/AppIconWhale.appiconset'
LAUNCH_ICON = APP / 'ios/Runner/Assets.xcassets/LaunchIconWhale.imageset'
OHOS = APP / 'ohos/AppScope/resources/base/media'
GLYPH_DART = APP / 'lib/ui/settings/appearance/whale_glyph.g.dart'

GRID = 108              # the launcher's grid, in dp
GLYPH_RADIUS = 35.0     # the whale's ink radius in that grid: the brand glyph's is 32.4, and
                        # this silhouette carries less ink, so it is nudged out to read the same
GLYPH = (43, 47, 54)    # the whale's flat colour, #2B2F36
FROM = (252, 253, 254)  # the card's gradient, sampled along the other icons' diagonal
TO = (231, 235, 242)
SQ_INSET = 0.0833       # the other legacy icons' margin (192 px: 16)
ROUND_INSET = 0.042     # ditto for ic_launcher_*_round.png (192 px: 8)
DIAG = (7.57, -5.87, 100.43, 113.87)  # ic_launcher_background.xml's gradient
DENSITIES = {'mdpi': 1.0, 'hdpi': 1.5, 'xhdpi': 2.0, 'xxhdpi': 3.0, 'xxxhdpi': 4.0}
LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
IOS_GLYPH = 0.75        # AppIconInk-1024.png's glyph is 75% of the side


def luminance(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def silhouette(art):
    """The whale alone: flat GLYPH, soft edges, transparent elsewhere (the eye a hole)."""
    out = Image.new('RGBA', art.size, GLYPH + (0,))
    src, dst = art.load(), out.load()
    for y in range(art.height):
        for x in range(art.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            # The white card is 0 and the whale 1, with a soft band between for the edges.
            cover = min(1.0, max(0.0, (150 - luminance((r, g, b))) / 80))
            dst[x, y] = GLYPH + (round(cover * a),)
    return out


def ink_centroid(img):
    sx = sy = n = 0
    for y in range(img.height):
        for x in range(img.width):
            a = img.getpixel((x, y))[3]
            if a > 8:
                sx += x * a
                sy += y * a
                n += a
    return sx / n, sy / n


def ink_radius(img, centre):
    r = 0.0
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] > 8:
                r = max(r, ((x - centre[0]) ** 2 + (y - centre[1]) ** 2) ** 0.5)
    return r


def gradient(size):
    """The card's gradient (the other icons' diagonal), full bleed, `size` px."""
    x0, y0, x1, y1 = DIAG
    img = Image.new('RGB', (size, size))
    px = img.load()
    ux, uy = x1 - x0, y1 - y0
    norm = ux * ux + uy * uy
    for y in range(size):
        ty = (y + 0.5) * GRID / size
        for x in range(size):
            tx = (x + 0.5) * GRID / size
            t = min(1.0, max(0.0, ((tx - x0) * ux + (ty - y0) * uy) / norm))
            px[x, y] = tuple(round(FROM[i] + (TO[i] - FROM[i]) * t) for i in range(3))
    return img


def foreground(size, sil):
    """The whale on a transparent 108 grid, `size` px, its ink centroid at the centre."""
    centre = ink_centroid(sil)
    scale = (GLYPH_RADIUS * size / GRID) / ink_radius(sil, centre)
    side = round(sil.width * scale)
    layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    layer.alpha_composite(sil.resize((side, side), Image.LANCZOS),
                          (round(size / 2 - centre[0] * scale), round(size / 2 - centre[1] * scale)))
    return layer


def round_icon(size, sil):
    """The round legacy icon: the adaptive layers cut to a circle, as 墨's round icon reads."""
    img = gradient(size).convert('RGBA')
    img.alpha_composite(foreground(size, sil))
    inner = round(size * (1 - 2 * ROUND_INSET))
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse(((size - inner) // 2, (size - inner) // 2,
                                  (size + inner) // 2, (size + inner) // 2), fill=255)
    out = img.convert('RGB')
    out.putalpha(mask)
    return out


def placed(art, size):
    """The master, inset like the other four legacy icons, on a transparent square."""
    inner = max(1, round(size * (1 - 2 * SQ_INSET)))
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    img.alpha_composite(art.resize((inner, inner), Image.LANCZOS),
                        ((size - inner) // 2, (size - inner) // 2))
    return img


def hex_colour(rgb):
    return '#FF%02X%02X%02X' % rgb


def write_android(art, sil):
    for density, scale in DENSITIES.items():
        dpi = RES / f'mipmap-{density}'
        placed(art, LEGACY[density]).save(dpi / 'ic_launcher_whale.png', optimize=True)
        round_icon(LEGACY[density], sil).save(dpi / 'ic_launcher_whale_round.png', optimize=True)
        foreground(round(GRID * scale), sil).save(dpi / 'ic_launcher_whale_foreground.png', optimize=True)

    (RES / 'drawable/ic_launcher_whale_background.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Launcher background, 白鲸 (whale): the card gradient of the source art, on the\n'
        "     default icon's diagonal (ic_launcher_background.xml). -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient\n'
        '                android:type="linear"\n'
        f'                android:startX="{DIAG[0]}"\n'
        f'                android:startY="{DIAG[1]}"\n'
        f'                android:endX="{DIAG[2]}"\n'
        f'                android:endY="{DIAG[3]}">\n'
        f'                <item android:offset="0" android:color="{hex_colour(FROM)}" />\n'
        f'                <item android:offset="1" android:color="{hex_colour(TO)}" />\n'
        '            </gradient>\n'
        '        </aapt:attr>\n'
        '    </path>\n'
        '</vector>\n'
    )

    for name in ('ic_launcher_whale', 'ic_launcher_whale_round'):
        (RES / f'mipmap-anydpi-v26/{name}.xml').write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Launcher icon 白鲸 (whale), API 26+: the card gradient, the whale, and the\n'
            '     whale again as the themed-icon layer (the foreground is a silhouette). -->\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_whale_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_whale_foreground" />\n'
            '    <monochrome android:drawable="@mipmap/ic_launcher_whale_foreground" />\n'
            '</adaptive-icon>\n'
        )


def write_ios(art, sil):
    IMMUTABLE.mkdir(parents=True, exist_ok=True)
    size = 1024
    img = gradient(size).convert('RGBA')
    side = round(sil.width * IOS_GLYPH)
    img.alpha_composite(sil.resize((side, side), Image.LANCZOS), ((size - side) // 2,) * 2)
    img.convert('RGB').save(IMMUTABLE / 'AppIconWhale-1024.png', optimize=True)
    LAUNCH_ICON.mkdir(parents=True, exist_ok=True)
    img.resize((512, 512), Image.LANCZOS).convert('RGB').save(LAUNCH_ICON / 'LaunchIconWhale.png', optimize=True)
    (LAUNCH_ICON / 'Contents.json').write_text(
        '{\n'
        '  "images": [\n'
        '    {\n'
        '      "filename": "LaunchIconWhale.png",\n'
        '      "idiom": "universal"\n'
        '    }\n'
        '  ],\n'
        '  "info": {\n'
        '    "author": "xcode",\n'
        '    "version": 1\n'
        '  }\n'
        '}\n'
    )
    (IMMUTABLE / 'Contents.json').write_text(
        '{\n'
        '  "images": [\n'
        '    {\n'
        '      "filename": "AppIconWhale-1024.png",\n'
        '      "idiom": "universal",\n'
        '      "platform": "ios",\n'
        '      "size": "1024x1024"\n'
        '    }\n'
        '  ],\n'
        '  "info": {\n'
        '    "author": "xcode",\n'
        '    "version": 1\n'
        '  }\n'
        '}\n'
    )


def write_ohos(sil):
    """The layered 1024 icon: the gradient, and the Android foreground scaled up."""
    OHOS.mkdir(parents=True, exist_ok=True)
    gradient(1024).save(OHOS / 'whale_background.png', optimize=True)
    foreground(1024, sil).save(OHOS / 'whale_foreground.png', optimize=True)
    (OHOS / 'icon_whale.json').write_text(
        '{\n  "layered-image": {\n'
        '    "background": "$media:whale_background",\n'
        '    "foreground": "$media:whale_foreground"\n'
        '  }\n}\n'
    )


def traced(sil):
    """potrace's outline of the silhouette, in the 108 grid.

    Returns [(start, [(kind, ...points)])]: `('c', c1, c2, end)` for a curve and
    `('l', end)` for a line. potrace writes one `M` / `c` / `l` / `z` path in 1/10 px, y up
    from the bottom; the grid wants y down from the top, 108 units over the whole layer.
    """
    bitmap = sil.getchannel('A').point(lambda v: 0 if v > 127 else 255).convert('1', dither=Image.Dither.NONE)
    with tempfile.TemporaryDirectory() as tmp:
        pbm, svg = Path(tmp) / 'whale.pbm', Path(tmp) / 'whale.svg'
        bitmap.save(pbm)
        subprocess.run(['potrace', '-s', '-o', str(svg), '--turdsize', '2', '--alphamax', '1',
                        '--opttolerance', '0.15', str(pbm)], check=True)
        data = re.search(r'<path[^>]*\sd="([^"]+)"', svg.read_text()).group(1)
    unit = GRID / sil.width  # 1 px -> grid units

    def point(x, y):
        return round(x * 0.1 * unit, 3), round((sil.height - y * 0.1) * unit, 3)

    subs, toks, i = [], re.findall(r'[MCLZmclz]|-?\d+(?:\.\d+)?', data), 0
    x = y = 0.0
    start = (0.0, 0.0)
    while i < len(toks):
        op = toks[i]
        i += 1
        if op in 'Mm':
            if op == 'm':
                x, y = x + float(toks[i]), y + float(toks[i + 1])
            else:
                x, y = float(toks[i]), float(toks[i + 1])
            i += 2
            start = (x, y)
            subs.append([point(x, y), []])
            continue
        if op in 'Zz':
            x, y = start  # the next `m` is relative to the subpath's start
            continue
        while i < len(toks) and toks[i] not in 'MCLZmclz':
            if op in 'Cc':
                c1 = (x + float(toks[i]), y + float(toks[i + 1]))
                c2 = (x + float(toks[i + 2]), y + float(toks[i + 3]))
                x, y = x + float(toks[i + 4]), y + float(toks[i + 5])
                i += 6
                subs[-1][1].append(('c', point(*c1), point(*c2), point(x, y)))
            else:
                x, y = x + float(toks[i]), y + float(toks[i + 1])
                i += 2
                subs[-1][1].append(('l', point(x, y)))
    return subs


def write_glyph(sil):
    subs = traced(sil)
    body = []
    for start, segments in subs:
        body.append('    ..moveTo(%s, %s)' % start)
        for segment in segments:
            if segment[0] == 'c':
                a, b, c = segment[1:]
                body.append(f'    ..cubicTo({a[0]}, {a[1]}, {b[0]}, {b[1]}, {c[0]}, {c[1]})')
            else:
                body.append('    ..lineTo(%s, %s)' % segment[1])
        body.append('    ..close()')
    lines = [
        '// GENERATED by tool/make_launcher_icon_whale.py from assets/brand/app-icon-whale.png.',
        '// Do not edit; change the master and run the tool.',
        '//',
        "// The whale of the 白鲸 launcher icon, in the launcher's 108 grid: the same placement",
        '// as mipmap-*/ic_launcher_whale_foreground.png, so the picker previews what the',
        '// launcher shows. One path, the eye and the belly as holes inside it.',
        '',
        '// dart format off',
        "import 'dart:ui' show Color, Path, PathFillType;",
        '',
        'abstract final class WhaleGlyph {',
        '  static const colour = Color(0xFF%02X%02X%02X);' % GLYPH,
        '',
        '  static final Path body = Path()',
        *body,
        '    ..fillType = PathFillType.evenOdd;',
        '}',
        '',
    ]
    GLYPH_DART.parent.mkdir(parents=True, exist_ok=True)
    GLYPH_DART.write_text('\n'.join(lines))


def main():
    art = Image.open(MASTER).convert('RGBA')
    sil = silhouette(art)
    write_android(art, sil)
    write_ios(art, sil)
    write_ohos(sil)
    write_glyph(sil)
    print(f'wrote 白鲸 from {MASTER.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
