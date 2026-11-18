"""Tunes/validates IdMatcher's auto-link thresholds with SIMULATED classifier
output (NOT real handwriting): per-digit error rate e, overconfident wrong
reads, ~2% blank boxes. Rosters are 44 learners, either RANDOM ids or
SEQUENTIAL ids (as schools often number: a long shared prefix + counter).
Reports how often a scan is auto-linked right / WRONG / flagged for review.
Mirrors IdMatcher.kt's scoring — keep the constants in sync."""
import math
import numpy as np

TEMPER = 0.5
EPS = 1e-3
MARGIN = 4.0
MIN_MEAN_LOGP = -1.4

def min_readable(n):
    """IdMatcher.minReadable(): at least 2/3 of the boxes, never fewer than 3."""
    return max(3, math.ceil(n * 2 / 3))

def score(probs, ident):
    t = 0.0
    for i, p in enumerate(probs):
        if p is None: continue
        q = p ** TEMPER; q = q / q.sum()
        t += np.log(max(q[int(ident[i])], EPS))
    return t

def decide(probs, roster):
    n = len(probs)
    scored = sorted(((score(probs, l), l) for l in roster), reverse=True)
    readable = sum(p is not None for p in probs)
    best = scored[0]; second = scored[1] if len(scored) > 1 else (-1e9, None)
    mean = best[0] / max(readable, 1)
    ok = readable >= min_readable(n) and mean >= MIN_MEAN_LOGP and (best[0] - second[0]) >= MARGIN
    return ok, best[1], [l for _, l in scored[:3]]

def fake_probs(ident, e, rng, blank=0.02):
    out = []
    for ch in ident:
        if rng.random() < blank: out.append(None); continue
        d = int(ch)
        if rng.random() < e: d = int(rng.choice([x for x in range(10) if x != d]))
        conf = rng.uniform(0.6, 0.999) if d != int(ch) else rng.uniform(0.9, 0.999)
        p = np.full(10, (1 - conf) / 9); p[d] = conf
        out.append(p)
    return out

def make_roster(n, kind, rng, size=44):
    if kind == 'random':
        s = set()
        while len(s) < size:
            s.add(''.join(str(x) for x in rng.integers(0, 10, n)))
        return list(s)
    start = int(rng.integers(0, 10 ** n - size)) if n > 4 else int(rng.integers(0, 10 ** n - size))
    return [str(start + i).zfill(n) for i in range(size)]

def trial(n, kind, e, rng):
    roster = make_roster(n, kind, rng)
    truth = roster[rng.integers(len(roster))]
    return truth, decide(fake_probs(truth, e, rng), roster)

if __name__ == '__main__':
    rng = np.random.default_rng(11)
    N = 1500
    for kind in ('random', 'sequential'):
        print(f'\n=== {kind} IDs, 44-learner roster ===')
        for n in (4, 6, 8, 12):
            line = []
            for e in (0.02, 0.05, 0.10, 0.20):
                right = wrong = flagged = 0
                for _ in range(N):
                    truth, (ok, best, cands) = trial(n, kind, e, rng)
                    if ok: right += best == truth; wrong += best != truth
                    else: flagged += 1
                line.append(f'e={e:.2f} right {right/N:.2f} WRONG {wrong/N:.4f}')
            print(f'{n:2d} digits: ' + ' | '.join(line))
