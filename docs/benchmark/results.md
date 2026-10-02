# Benchmark results

Measured locally with `docs/benchmark/single-request.sh`, a single
sequential request per language, immediately after server start
(cold) and again right after (warm).

| Language | run phase only (ms) | full HTTP round-trip (s), cold → warm |
|---|---|---|
| C++ | ~204 | 1.178 → 1.073 |
| C | ~195 | 0.699 → 0.577 |
| Java | ~226 | 1.349 → 1.143 |
| Python | ~216 | 0.739 → 0.688 |
| JavaScript | ~233 | 0.546 → 0.558 |

## Observations

- The run phase itself (`RunResult.timeMs`) is consistent cold vs
  warm for every language — it only measures execution, not
  compilation, and the sandbox container itself has no meaningful
  "warm-up" cost on a local Docker daemon (image is already pulled).
- The gap between `timeMs` and the full HTTP round-trip is almost
  entirely compile time, which is currently **not** reported back to
  the client. For compiled languages (C++, Java) this is roughly
  800ms-1s, dwarfing the ~200ms run phase. A natural follow-up would
  be to report `compileTimeMs` alongside `timeMs` in the API
  response.
- Interpreted languages (Python, JS) have a lower total round-trip
  since their "compile" phase is just a syntax check
  (`py_compile`/`node --check`), not real compilation.

## Load behavior

See `docs/benchmark/load-test.md` for queue and rate-limiter behavior
under concurrent load (first measured in Day 5, re-verified here).
