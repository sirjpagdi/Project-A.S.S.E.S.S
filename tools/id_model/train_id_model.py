#!/usr/bin/env python3
"""Trains the small digit classifier used to read handwritten ID boxes and
writes app/src/main/assets/id_digit_model.json.

Base data: scikit-learn's bundled 8x8 handwritten digits (1,797 samples,
offline, no download). Each is upscaled to 32x32, then randomly re-drawn with
different stroke thickness, slant, rotation and position, and pushed through
the SAME feature pipeline the app uses (id_features.py), so training and
scanning see digits the same way.

To teach it YOUR learners' handwriting (strongly recommended):
  1. collect labeled crops: PNG/JPG files named like  7_0001.png  (first
     character = the digit), each ONE ID box cropped from a real scan WITH
     its margin — the box plus 10% of its width on each side and 28% of its
     height above and below, exactly as the app's reader crops it
  2. run:  python3 train_id_model.py --samples path/to/crops
  Real samples are augmented REAL_AUG times and repeated REAL_REPEAT times so
  a few hundred of them outweigh the synthetic base.

The folder real_samples/ next to this script holds real handwritten ID digits
from learners' scanned sheets (named <digit>_<sheet><position>.png, margin
included). It is ALWAYS used, so every retraining keeps them; --samples adds
more on top. Measured on unseen sheets (train on 6, test on the 7th), adding
just those 84 real digits took the model from 58% to 79% digit accuracy —
real handwriting is what it needs.

Both the straightened (slant-corrected) and raw form of every digit are
trained, so the model reads well whether or not the app's slant correction
is on.
"""
import argparse, glob, json, os
import numpy as np
import cv2
from sklearn.datasets import load_digits
from sklearn.neural_network import MLPClassifier
from sklearn.model_selection import train_test_split
from id_features import FEATURE_KIND, FEATURE_SCALE, clean_ink, clean_ink_outset, deslant_mask, features_from_binary

REAL_AUG = 20       # augmented copies made of every real digit
REAL_REPEAT = 10    # then each real digit and its copies are repeated this often
SAMPLES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'real_samples')
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'id_digit_model.json')


def base_binary(img8):
    big = cv2.resize(img8.astype(np.float32) / 16.0, (32, 32), interpolation=cv2.INTER_CUBIC)
    return (big > 0.45).astype(np.uint8) * 255


def augment(ink, rng):
    h, w = ink.shape
    out = ink
    # stroke thickness: erode / dilate by 0..2 px
    k = rng.integers(-1, 3)
    if k > 0:
        out = cv2.dilate(out, np.ones((2 * k + 1, 2 * k + 1), np.uint8))
    elif k < 0:
        out = cv2.erode(out, np.ones((2, 2), np.uint8))
    # rotation + shear + scale jitter
    ang = rng.normal(0, 7)
    shear = rng.normal(0, 0.12)
    sc = rng.uniform(0.85, 1.05)
    M = cv2.getRotationMatrix2D((w / 2, h / 2), ang, sc)
    M[0, 1] += shear
    pad = cv2.copyMakeBorder(out, 12, 12, 12, 12, cv2.BORDER_CONSTANT, value=0)
    M[0, 2] += 12 * 0 + rng.integers(-2, 3)
    M[1, 2] += rng.integers(-2, 3)
    out = cv2.warpAffine(pad, M, (pad.shape[1], pad.shape[0]), flags=cv2.INTER_LINEAR)
    # speckle / dropout to mimic pen skips
    if rng.random() < 0.3:
        noise = (rng.random(out.shape) < 0.02).astype(np.uint8) * 255
        out = cv2.bitwise_and(out, cv2.bitwise_not(noise))
    return (out > 127).astype(np.uint8) * 255


TRAIN_FONTS = [cv2.FONT_HERSHEY_SIMPLEX, cv2.FONT_HERSHEY_DUPLEX,
               cv2.FONT_HERSHEY_COMPLEX, cv2.FONT_HERSHEY_SCRIPT_SIMPLEX]
# Deliberately NOT trained on, used only by simulate_check.py as an
# out-of-sample check: TRIPLEX, SCRIPT_COMPLEX, COMPLEX_SMALL, italic.


def font_ink(digit, font, rng):
    """One rendered digit as a tight ink mask, with random weight/size/jitter."""
    img = np.zeros((99, 115), np.uint8)
    scale = rng.uniform(1.6, 2.4)
    th = int(rng.integers(2, 6))
    (tw, tht), _ = cv2.getTextSize(str(digit), font, scale, th)
    cv2.putText(img, str(digit), (int((115 - tw) / 2), int((99 + tht) / 2)), font, scale, 255, th, cv2.LINE_AA)
    return (img > 127).astype(np.uint8) * 255


def feats(ink):
    if ink is None or ink.max() == 0:
        return None
    return features_from_binary(ink)


def straight(ink):
    """The slant-corrected form of an ink mask (what the app reads after v1.2.3)."""
    return deslant_mask(ink > 0).astype(np.uint8) * 255


def maybe_straight(ink, rng):
    """Half of the synthetic digits are straightened, half left raw."""
    return straight(ink) if rng.random() < 0.5 else ink


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--samples', help='folder of labeled real crops, e.g. 7_0001.png')
    ap.add_argument('--aug', type=int, default=24, help='augmented copies per base digit')
    ap.add_argument('--hidden', type=int, default=128)
    ap.add_argument('--font-copies', type=int, default=150)
    args = ap.parse_args()

    rng = np.random.default_rng(1234)
    d = load_digits()
    idx_train, idx_test = train_test_split(np.arange(len(d.target)), test_size=0.2,
                                           random_state=7, stratify=d.target)

    def build(indices, copies):
        X, y = [], []
        for i in indices:
            b = base_binary(d.images[i])
            for c in range(copies):
                f = feats(maybe_straight(augment(b, rng) if c else b, rng))
                if f is not None:
                    X.append(f); y.append(d.target[i])
        return np.array(X), np.array(y)

    Xtr, ytr = build(idx_train, args.aug)
    Xte, yte = build(idx_test, 4)

    # Typeface diversity: gives the model many clean digit shapes beyond the
    # one handwriting source, which matters for neat writers.
    fx, fy = [], []
    for font in TRAIN_FONTS:
        for digit in range(10):
            for c in range(args.font_copies):
                f = feats(maybe_straight(augment(font_ink(digit, font, rng), rng), rng))
                if f is not None:
                    fx.append(f); fy.append(digit)
    Xtr = np.vstack([Xtr, np.array(fx)])
    ytr = np.concatenate([ytr, np.array(fy)])

    paths = sorted(glob.glob(os.path.join(SAMPLES_DIR, '*')))
    if args.samples:
        paths += sorted(glob.glob(os.path.join(args.samples, '*')))
    rx, ry = [], []
    # With few real digits, make many augmented copies of each; with hundreds, a
    # few are plenty (and training stays quick).
    real_aug = REAL_AUG if len(paths) < 300 else 8
    for p in paths:
        name = os.path.basename(p)
        if not name[:1].isdigit():
            continue
        g = cv2.imread(p, cv2.IMREAD_GRAYSCALE)
        if g is None:
            continue
        label = int(name[0])
        for straighten in (False, True):
            ink = clean_ink_outset(g, straighten=straighten)   # same cleanup the app runs
            if ink is None:
                continue
            f = feats(ink)
            if f is not None:
                rx.append(f); ry.append(label)
            for _ in range(real_aug):
                a = augment(ink, rng)
                a = maybe_straight(a, rng)
                f = feats(a)
                if f is not None:
                    rx.append(f); ry.append(label)
    print(f'real digit crops: {len(paths)}, training rows made from them: {len(rx)}')
    if rx:
        # repeat the real rows until they are roughly as numerous as the synthetic
        # ones (at most REAL_REPEAT times) so real handwriting carries the model
        repeat = int(min(REAL_REPEAT, max(1, round(len(Xtr) / len(rx)))))
        print(f'real rows repeated x{repeat}')
        Xtr = np.vstack([Xtr] + [np.array(rx)] * repeat)
        ytr = np.concatenate([ytr] + [np.array(ry)] * repeat)

    clf = MLPClassifier(hidden_layer_sizes=(args.hidden,), activation='relu', alpha=1e-3,
                        max_iter=300, early_stopping=True, random_state=1)
    scale = FEATURE_SCALE[FEATURE_KIND]
    clf.fit(Xtr / scale, ytr)
    acc = clf.score(Xte / scale, yte)
    print(f'train samples {len(ytr)}, held-out accuracy (same-writer split, optimistic): {acc:.3f}')

    model = {
        'format': 'mlp-v1',
        'input': int(Xtr.shape[1]),
        'features': FEATURE_KIND,   # which extractor the app must use (IdReader)
        'scale': scale,
        'w1': clf.coefs_[0].T.round(5).tolist(),   # [hidden][input]
        'b1': clf.intercepts_[0].round(5).tolist(),
        'w2': clf.coefs_[1].T.round(5).tolist(),   # [10][hidden]
        'b2': clf.intercepts_[1].round(5).tolist(),
    }
    with open(OUT, 'w') as f:
        json.dump(model, f, separators=(',', ':'))
    print('wrote', os.path.abspath(OUT), os.path.getsize(OUT), 'bytes')


if __name__ == '__main__':
    main()
