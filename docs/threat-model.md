# Threat model

This service executes arbitrary, untrusted code submitted by anyone
with access to it. This document describes what is protected against,
how, and what is explicitly out of scope.

## What's protected, and how

| Threat | Mitigation |
|---|---|
| Infinite loop / hang | Background timer kills the container after 5s if it hasn't exited |
| Memory exhaustion | `--memory` + `--memory-swap` (128 MB, swap disabled) |
| Fork bomb / process exhaustion | `--pids-limit 64` |
| Network access (exfiltration, abuse, attacking other hosts) | `--network none` |
| Writing to the container filesystem / host | `--read-only`, with a small `--tmpfs /tmp`; the submitted-code directory is mounted read-only during execution |
| Privilege escalation | `--cap-drop ALL`, `--security-opt no-new-privileges` |
| Running as root inside the container | Each runner image creates and uses a non-root `runner` user |
| Unbounded stdout/stderr (disk/memory pressure, slow clients) | Output capped at 64 KB per stream; exceeding it kills the container early |
| Container left running after its process exits, for any reason | `docker kill` is called unconditionally after `waitFor()` returns, not only on timeout (`docs/decisions/002`) |
| Server overload from many concurrent requests | `RunQueue`: 4 worker threads, 20-slot bounded queue; full queue returns `429` immediately instead of queueing indefinitely |
| One client monopolizing the service | `RateLimiter`: per-IP token bucket, 10 tokens, refilled at 10/minute |
| Oversized request bodies | Source and stdin capped at 64 KB each, measured in UTF-8 bytes (not characters) |
| XSS via program output or shared code | Program output and loaded snippets are rendered with `textContent`, never `innerHTML`, on the frontend |

## Known limitations

- **Docker is not a hardened sandbox.** Containers share the host kernel. The layered defenses above (capabilities, no network, resource limits, read-only filesystem, non-root user) substantially reduce the attack surface, but a kernel-level container-escape vulnerability would not be stopped by any of this. A stronger isolation layer (gVisor) was evaluated and intentionally not adopted, given the time budget for this project — see Day 7 notes.
- **Exit code 137 is treated as "memory limit exceeded",** but the same code is produced by any `SIGKILL`, including the timeout killer racing with an OOM kill. In practice this only means a borderline case might be reported as `MEMORY_LIMIT` instead of `TIME_LIMIT` or vice versa — the code was still stopped either way.
- **The rate limiter's token buckets are never evicted.** For the traffic this project is expected to see, this is a non-issue; under real public traffic it would need a TTL (`docs/decisions/005`).
- **`RateLimiter` keys on `HttpServletRequest.getRemoteAddr()`,** which is the direct TCP peer. Behind a reverse proxy that isn't on the same host as the client (not the case here — nginx and the backend run as sibling containers on one machine), this would need to read `X-Forwarded-For` instead, or every visitor would share one bucket.
- **The Docker socket is mounted into the backend container** (`docker-compose.yml`) so `DockerRunner` can create sibling containers on the host (Docker-out-of-Docker, not Docker-in-Docker). Anything with code-execution access to the backend process effectively has control over the host's Docker daemon. This is why the project is not deployed to a shared or multi-purpose machine, and not exposed publicly (see below).
- **No persistent audit log of executed code.** Snippets are stored only when explicitly shared via the Share button; ordinary runs are not logged or retained anywhere beyond the request/response cycle.

## Why this isn't deployed publicly

Running a public code-execution service carries real, ongoing risk and
cost: it's a target for abuse (cryptomining, DDoS staging, scanning),
and the Docker-socket access described above means a compromise of
the backend is effectively a compromise of its host. For a portfolio
project, that risk and the ongoing hosting cost aren't justified by
the benefit of a live link. The project runs fully locally with one
command (`make up`) and is demonstrated via the README's demo GIF
instead. See the README for how to run it yourself.