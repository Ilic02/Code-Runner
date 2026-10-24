# Code Runner

Online execution of C, C++, Java, Python and JavaScript code in an
isolated Docker sandbox, with a web editor.

<!-- TODO: demo GIF goes here — docs/img/demo.gif -->

## What it is

Type code in the browser, hit Run, and it's compiled (or syntax-checked)
and executed inside a locked-down Docker container — no network access,
a memory and process limit, a time limit, read-only filesystem, and a
non-root user. The result (stdout, stderr, exit code, and a verdict
like `OK`, `COMPILE_ERROR`, `TIME_LIMIT`) comes back in under a second
for most programs. Code can also be shared via a short link.

Built to explore what it actually takes to run untrusted code safely:
not just "call `docker run`", but resource limits, a bounded job queue,
rate limiting, and the handful of real bugs that only show up once you
try to break your own sandbox — all documented as they were found, in
`docs/decisions/`.

## Features

- Five languages: C++, C, Java, Python, JavaScript
- Full sandbox: memory/process/time limits, no network, read-only
  filesystem, dropped capabilities, non-root execution
- Bounded job queue (4 workers) and per-IP rate limiting — a busy or
  abusive client gets a fast `429`, not an unbounded pile of containers
- Share any snippet via a short link
- 6 adversarial "escape" tests (fork bomb, infinite loop, memory hog,
  huge output, network access attempt, disk write attempt) run in CI
  on every push, proving the sandbox actually holds

## Running it

Requires Docker and Docker Compose.

```bash
git clone https://github.com/<your-username>/code-runner.git
cd code-runner
make up
```

Open http://localhost:8081

## Running it for development

Without Docker Compose, backend and frontend run separately:

```bash
# build the sandbox images once
make runners

# terminal 1
cd backend
./gradlew bootRun

# terminal 2
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173 (the Vite dev server proxies `/api/*` to
the backend on port 8080).

## Running the tests

```bash
make runners   # sandbox images are required by the tests
make test
```

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for the request
lifecycle and a diagram.

## Security

See [`docs/threat-model.md`](docs/threat-model.md) for what's
protected against, how, and what's explicitly out of scope — including
why this project isn't deployed publicly.

## Design decisions and bugs found along the way

[`docs/decisions/`](docs/decisions/) has short write-ups of the
non-obvious choices and the real bugs hit while building this,
roughly in the order they came up:

- Killing containers unconditionally, not just on timeout
- Why the sandbox's temp directory has to be world-writable
- Why execution doesn't pin a fixed container UID
- The job queue and rate limiter's parameters
- A flaky escape test and what was actually wrong with it

## Benchmarks

See [`docs/benchmark/`](docs/benchmark/) for per-language latency
(cold vs. warm) and load test results for the queue and rate limiter.

## Tech stack

- **Backend**: Java 25, Spring Boot, plain JDBC (SQLite) — no ORM
- **Frontend**: TypeScript, Vite, Monaco Editor
- **Sandboxing**: Docker, one image per language
- **CI**: GitHub Actions — backend tests, frontend build, and the
  escape-test suite run on every push

## Development process

Built over about two weeks, working through the project day by day
with Claude as a pair-programming assistant — planning the structure,
writing code, and debugging issues together, including the real bugs
documented in `docs/decisions/`. Every architectural decision,
trade-off and fix in this repository was made deliberately and
verified by running it, not accepted blindly.

## License

MIT