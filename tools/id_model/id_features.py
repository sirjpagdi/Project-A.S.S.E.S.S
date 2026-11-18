"""Digit feature extraction for the ID reader.

THIS FILE IS THE REFERENCE for app/src/main/java/.../IdDigitClassifier.kt's
`featuresFromBinary()` and IdReader.kt's crop cleanup. If you change a number
here, change the Kotlin side too (and retrain), or the app and the model will
disagree about what a digit looks like.

NOTE (v1.2.3): the app's crop cleanup is now `clean_ink_outset()` below
(box + margin, printed lines removed, blobs kept by position). The older
`clean_ink()` (10% inset, drop every blob touching the crop edge) discarded
real, large handwriting and is kept only for the synthetic training digits
and tools/id_model/simulate_check.py. The feature step
(`features_from_binary`) is UNCHANGED, so the trained model did not need
retraining for the v1.2.3 reader fix.

Pipeline for one ID box crop (grayscale, dark ink on light paper) — the
ORIGINAL (<= v1.2.2) cleanup, see clean_ink():
  1. inset the crop by INSET so the printed box lines are not inside it
  2. Otsu threshold, ink = white
  3. drop connected components that touch the crop edge (leftover box lines)
  4. drop specks smaller than MIN_COMPONENT_FRACTION of the crop area
  5. blank if the remaining ink is under BLANK_INK_FRACTION of the crop
  6. bounding box of the remaining ink -> scaled to fit 32x32 keeping aspect
     ratio, centered, binarized at 50%
  6b. stroke-thickness normalization: the training digits are bold (about 55%
     ink inside the digit's bounding box) but a pen stroke is much thinner, so
     dilate with a 3x3 kernel (up to MAX_THICKEN times) until the ink covers
     at least TARGET_INK_FRACTION of the bounding box of the digit
  7. 4x4 block sums -> 8x8 grid of counts 0..16 (the same representation as
     scikit-learn's bundled 8x8 handwritten-digits set the model is trained on)
"""
import numpy as np
import cv2

INSET = 0.10
MIN_COMPONENT_FRACTION = 0.004
BLANK_INK_FRACTION = 0.012
FRAME = 32
TARGET_INK_FRACTION = 0.45
MAX_THICKEN = 3


def clean_ink(gray_crop):
    """gray_crop: uint8 HxW (dark ink on light). Returns (ink_mask uint8 0/255 or None if blank)."""
    h, w = gray_crop.shape
    dy, dx = int(round(h * INSET)), int(round(w * INSET))
    crop = gray_crop[dy:h - dy, dx:w - dx]
    if crop.size == 0:
        return None
    _, binary = cv2.threshold(crop, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(binary, connectivity=8)
    ch, cw = binary.shape
    keep = np.zeros_like(binary)
    min_area = MIN_COMPONENT_FRACTION * ch * cw
    for i in range(1, n):
        x, y, bw, bh, area = stats[i]
        touches = x <= 0 or y <= 0 or x + bw >= cw or y + bh >= ch
        if touches or area < min_area:
            continue
        keep[labels == i] = 255
    if (keep > 0).sum() < BLANK_INK_FRACTION * ch * cw:
        return None
    return keep


# Which description of the 32x32 digit frame the model reads (v1.2.3):
#   'hog-v1'      stroke-direction histograms (HOG), 324 numbers  <- current
#   'blocks-8x8'  the original 8x8 grid of 4x4 block sums, 64 numbers
# Measured on learners the model never saw: 91.3% digits right with HOG against
# 87.8% with block sums (HOG fixed 39 digits the old one got wrong and broke 18).
# The app's IdReader.featuresFromInk must build the SAME one; the model JSON
# records which ("features") and the app picks the matching extractor.
FEATURE_KIND = 'hog-v1'
FEATURE_SCALE = {'blocks-8x8': 16.0, 'hog-v1': 1.0}
HOG_GAIN = 4.0     # HOG values are small (<= ~0.4); x4 brings them to the same range as the net's other inputs
HOG = cv2.HOGDescriptor((32, 32), (16, 16), (8, 8), (8, 8), 9)   # win, block, stride, cell, bins


def frame_from_binary(ink):
    """ink: uint8 0/255 mask with at least some ink. Returns the 32x32 0/1 digit frame."""
    ys, xs = np.nonzero(ink)
    crop = ink[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = crop.shape
    s = FRAME / max(h, w)
    nw, nh = max(1, int(round(w * s))), max(1, int(round(h * s)))
    resized = cv2.resize(crop, (nw, nh), interpolation=cv2.INTER_AREA)
    frame = np.zeros((FRAME, FRAME), np.uint8)
    oy, ox = (FRAME - nh) // 2, (FRAME - nw) // 2
    frame[oy:oy + nh, ox:ox + nw] = (resized >= 128).astype(np.uint8)
    # 6b: thicken thin strokes (the box is the digit's bounding box in the frame)
    kernel = np.ones((3, 3), np.uint8)
    for _ in range(MAX_THICKEN):
        box = frame[oy:oy + nh, ox:ox + nw]
        if box.size == 0 or box.mean() >= TARGET_INK_FRACTION:
            break
        frame = cv2.dilate(frame, kernel)
    return frame


def features_from_binary(ink, kind=None):
    """Model input for one cleaned digit mask — see FEATURE_KIND."""
    kind = kind or FEATURE_KIND
    frame = frame_from_binary(ink)
    if kind == 'hog-v1':
        return HOG.compute((frame * 255).astype(np.uint8)).reshape(-1).astype(np.float32) * HOG_GAIN
    return frame.reshape(8, 4, 8, 4).sum(axis=(1, 3)).astype(np.float32).reshape(-1)


# ---------------------------------------------------------------------------
# v1.2.3 cleanup — mirrors IdReader.featuresFor() in the app. Input is the
# box PLUS a margin (OUTSET_X / OUTSET_Y of the box size on every side), cut
# from the warped sheet exactly like the app does. Returns an ink mask
# (uint8 0/255) or None for a blank box.
# ---------------------------------------------------------------------------
OUTSET_X = 0.10
OUTSET_Y = 0.28
H_LINE_LEN = 0.60
V_LINE_LEN = 0.80
H_BAND = 0.20
V_BAND = 0.14
MIN_CONTRAST = 55
CENTROID_TOLERANCE = -0.04   # negative = inset (v1.2.3, mirrors IdReader)
MAX_SKEW = 0.35
MIN_SKEW = 0.08
RELATIVE_PART_FRACTION = 0.10
BLANK_INK_FRACTION_V2 = 0.006
BLANK_MIN_EXTENT = 0.15


def clean_ink_outset(gray, straighten=True):
    ch, cw = gray.shape
    bw = int(round(cw / (1 + 2 * OUTSET_X)))
    bh = int(round(ch / (1 + 2 * OUTSET_Y)))
    if bw < 8 or bh < 8:
        return None
    nx0, ny0 = int(round(bw * OUTSET_X)), int(round(bh * OUTSET_Y))
    nx1, ny1 = nx0 + bw, ny0 + bh
    flat = np.sort(gray.reshape(-1))
    if np.median(flat) - flat[:max(1, len(flat) // 33)].mean() < MIN_CONTRAST:
        return None
    _, b = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)
    # printed lines: long straight runs near the expected box edges
    hk = max(5, int(H_LINE_LEN * bw))
    hl = cv2.morphologyEx(cv2.dilate(b, np.ones((3, 1), np.uint8)), cv2.MORPH_OPEN,
                          cv2.getStructuringElement(cv2.MORPH_RECT, (hk, 1)))
    hband = np.zeros_like(b)
    band_y = int(round(H_BAND * bh))
    for ey in (ny0, ny1):
        hband[max(0, ey - band_y):min(ch, ey + band_y + 1), :] = 255
    hl = cv2.bitwise_and(hl, hband)
    vk = max(5, int(V_LINE_LEN * bh))
    vl = cv2.morphologyEx(cv2.dilate(b, np.ones((1, 3), np.uint8)), cv2.MORPH_OPEN,
                          cv2.getStructuringElement(cv2.MORPH_RECT, (1, vk)))
    vband = np.zeros_like(b)
    band_x = int(round(V_BAND * bw))
    for ex in (nx0, nx1):
        vband[:, max(0, ex - band_x):min(cw, ex + band_x + 1)] = 255
    vl = cv2.bitwise_and(vl, vband)
    lines = cv2.bitwise_or(hl, vl)
    ink = cv2.bitwise_and(b, cv2.bitwise_not(lines))
    # bridge the gaps a removed line leaves in a stroke that crossed it
    gv = cv2.morphologyEx(ink, cv2.MORPH_CLOSE, np.ones((5, 1), np.uint8))
    gh = cv2.morphologyEx(ink, cv2.MORPH_CLOSE, np.ones((1, 5), np.uint8))
    ink = cv2.bitwise_or(ink, cv2.bitwise_and(cv2.bitwise_or(gv, gh), lines))
    # blobs that belong to THIS box
    n, lab, st, cen = cv2.connectedComponentsWithStats(ink, connectivity=8)
    area = bw * bh
    tol_x, tol_y = CENTROID_TOLERANCE * bw, CENTROID_TOLERANCE * bh
    cand = [k for k in range(1, n)
            if nx0 - tol_x <= cen[k][0] <= nx1 + tol_x
            and ny0 - tol_y <= cen[k][1] <= ny1 + tol_y
            and st[k][4] >= MIN_COMPONENT_FRACTION * area]
    if not cand:
        return None
    largest = max(st[k][4] for k in cand)
    keep = np.zeros(n, bool)
    for k in cand:
        if st[k][4] >= RELATIVE_PART_FRACTION * largest:
            keep[k] = True
    mask = keep[lab] & (lab > 0)
    ys, xs = np.nonzero(mask)
    if mask.sum() < BLANK_INK_FRACTION_V2 * area:
        return None
    if max(ys.max() - ys.min() + 1, xs.max() - xs.min() + 1) < BLANK_MIN_EXTENT * bh:
        return None
    if straighten:
        mask = deslant_mask(mask)
    return mask.astype(np.uint8) * 255


def deslant_mask(mask):
    """Undo handwriting slant — mirrors IdReader.deslant(): shift every row back by
    the ink's own mu11/mu02 drift (whole pixels, no resampling). Only clear slant
    (MIN_SKEW..MAX_SKEW) is corrected."""
    m = mask > 0
    ys, xs = np.nonzero(m)
    if len(xs) < 10:
        return mask
    xc, yc = xs.mean(), ys.mean()
    mu02 = ((ys - yc) ** 2).sum()
    mu11 = ((xs - xc) * (ys - yc)).sum()
    if mu02 < 1e-6:
        return mask
    skew = float(np.clip(mu11 / mu02, -MAX_SKEW, MAX_SKEW))
    if abs(skew) < MIN_SKEW:
        return mask
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    hgt, wid = y1 - y0 + 1, x1 - x0 + 1
    pad = int(np.ceil(abs(skew) * hgt)) + 1
    out = np.zeros((hgt, wid + 2 * pad), bool)
    for y in range(y0, y1 + 1):
        row = m[y, x0:x1 + 1]
        if row.any():
            shift = int(round(-skew * (y - yc)))
            out[y - y0, pad + shift:pad + shift + wid] = row
    return out
