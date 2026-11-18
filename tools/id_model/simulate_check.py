"""Sanity check: renders digits with OpenCV's built-in Hershey fonts (NOT the
training data's handwriting) inside printed-looking boxes, runs the app's crop
cleanup + the exported model, and reports per-digit accuracy. Printed-style
glyphs are not real handwriting — this only proves the plumbing (box lines
removed, centering, model JSON) works. Real accuracy needs real samples."""
import json, numpy as np, cv2
from id_features import clean_ink, features_from_binary

m = json.load(open('../../app/src/main/assets/id_digit_model.json'))
W1, b1, W2, b2 = map(np.array, (m['w1'], m['b1'], m['w2'], m['b2']))
def predict(f):
    h = np.maximum(0, W1 @ (f / m['scale']) + b1)
    z = W2 @ h + b2
    p = np.exp(z - z.max()); return p / p.sum()

# Held-out fonts only (the trainer never renders these):
fonts = [cv2.FONT_HERSHEY_TRIPLEX, cv2.FONT_HERSHEY_SCRIPT_COMPLEX,
         cv2.FONT_HERSHEY_COMPLEX_SMALL, cv2.FONT_ITALIC | cv2.FONT_HERSHEY_SIMPLEX]
rng = np.random.default_rng(5)
ok = tot = blank_ok = 0
conf = np.zeros((10, 10), int)
for trial in range(1500):
    d = int(rng.integers(0, 10))
    img = np.full((99, 115), 235, np.uint8)
    cv2.rectangle(img, (2, 2), (112, 96), 20, 3)
    font = fonts[rng.integers(len(fonts))]
    scale = rng.uniform(1.6, 2.4); th = int(rng.integers(2, 5))
    (tw, tht), _ = cv2.getTextSize(str(d), font, scale, th)
    x = int((115 - tw) / 2 + rng.integers(-6, 7)); y = int((99 + tht) / 2 + rng.integers(-5, 6))
    cv2.putText(img, str(d), (x, y), font, scale, 30, th, cv2.LINE_AA)
    img = cv2.GaussianBlur(img, (3, 3), 0)
    img = np.clip(img.astype(int) + rng.normal(0, 6, img.shape), 0, 255).astype(np.uint8)
    ink = clean_ink(img)
    if ink is None:
        continue
    p = predict(features_from_binary(ink)); tot += 1; ok += int(p.argmax() == d); conf[d, p.argmax()] += 1
blank = np.full((99, 115), 235, np.uint8); cv2.rectangle(blank, (2, 2), (112, 96), 20, 3)
print('blank box detected as blank:', clean_ink(blank) is None)
print(f'held-out-font digit accuracy: {ok}/{tot} = {ok/tot:.3f}')
print('worst confusions:', sorted([(conf[i, j], i, j) for i in range(10) for j in range(10) if i != j], reverse=True)[:5])
