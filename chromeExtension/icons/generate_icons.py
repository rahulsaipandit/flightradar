from PIL import Image, ImageDraw
import math

GREEN = (92, 255, 92, 255)
YELLOW = (255, 204, 0, 255)
WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)
RED = (220, 30, 30, 255)
BLUE = (40, 100, 220, 255)

def make(size):
    scale = 4
    S = size * scale
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cx, cy = S / 2, S / 2

    # Radar: thick concentric rings (innermost to outermost), faint cross.
    max_r = S * 0.46
    ring_width = max(1, int(S * 0.075))
    ring_colors = [BLUE, GREEN, RED]  # innermost -> outermost
    for frac, color in zip((0.45, 0.72, 1.0), ring_colors):
        r = max_r * frac
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=ring_width)
    cross_w = max(1, int(S * 0.035))
    d.line([cx - max_r, cy, cx + max_r, cy], fill=WHITE, width=cross_w)
    d.line([cx, cy - max_r, cx, cy + max_r], fill=WHITE, width=cross_w)

    # Radar sweep line, pointing toward 2 o'clock.
    sweep_angle = math.radians(60)  # clockwise from 12 o'clock
    sweep_x = cx + max_r * math.sin(sweep_angle)
    sweep_y = cy - max_r * math.cos(sweep_angle)
    sweep_w = max(1, int(S * 0.008))
    d.line([cx, cy, sweep_x, sweep_y], fill=BLACK, width=sweep_w)

    # Airplane silhouette (top-down), yellow, centered on the radar.
    L = S * 0.62  # nose-to-tail span
    top = cy - L / 2
    bottom = cy + L / 2
    fuselage_w = S * 0.045
    wing_y = cy - L * 0.02
    wing_half = S * 0.30
    wing_chord = S * 0.10
    tail_y = bottom - S * 0.06
    tail_half = S * 0.13
    tail_chord = S * 0.06
    nose = (cx, top)

    plane = [
        (cx, top),
        (cx + fuselage_w, wing_y - wing_chord * 0.3),
        (cx + wing_half, wing_y + wing_chord),
        (cx + fuselage_w * 1.3, wing_y + wing_chord * 1.4),
        (cx + fuselage_w, bottom - S * 0.1),
        (cx + tail_half, tail_y + tail_chord),
        (cx + fuselage_w * 0.6, tail_y),
        (cx, bottom),
        (cx - fuselage_w * 0.6, tail_y),
        (cx - tail_half, tail_y + tail_chord),
        (cx - fuselage_w, bottom - S * 0.1),
        (cx - fuselage_w * 1.3, wing_y + wing_chord * 1.4),
        (cx - wing_half, wing_y + wing_chord),
        (cx - fuselage_w, wing_y - wing_chord * 0.3),
    ]
    plane_outline_w = max(1, int(S * 0.008))
    d.polygon(plane, fill=YELLOW, outline=BLACK, width=plane_outline_w)

    im = im.resize((size, size), Image.LANCZOS)
    im.save(f"icon{size}.png")

for s in (16, 48, 128):
    make(s)
print("done")
