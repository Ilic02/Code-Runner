# Load test: queue and rate limiter

Measured locally against `./gradlew bootRun`, using small shell
scripts that fire concurrent `curl` requests at `POST /api/runs`
(see commands below). All requests originate from `127.0.0.1`, so
they share a single rate-limiter bucket.

## 50 concurrent requests, same source (bucket exhausted)

```bash
for i in $(seq 1 50); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/runs \
    -H "Content-Type: application/json" \
    -d '{"language":"cpp","source":"int main(){return 0;}","stdin":""}' &
done
wait
```

Result: **10× `200`, 40× `429`**, total wall time ~3.0s.

This matches the rate limiter's bucket capacity exactly (10 tokens).
The 40 rejected requests fail fast (no queueing, no Docker
involvement), which is the point of checking the rate limit before
the job queue.

## 4 concurrent requests, after the bucket has refilled

Same 4-request script, run once right after the 50-request test
(bucket nearly empty: 2× `429`, 2× `200`), and again after waiting
60s for the bucket to refill (10 tokens/minute):

| Run | Result | Wall time |
|---|---|---|
| Immediately after the 50-request test | 2× `429`, 2× `200` (the 2 successes ran in parallel: ~0.8s each, ~0.83s total, not ~1.6s) | 0.832s |
| After `sleep 60` | 4× `200`, all in parallel | ~0.8s |

The second run confirms two things: the bucket refill rate behaves as
configured (10/minute), and the 4 requests that do pass the rate
limiter are genuinely processed concurrently by the 4 `RunQueue`
workers — total wall time stays close to a single request's time
rather than growing with the request count.

## Known limitation

All requests in local testing share one IP (`127.0.0.1`), so this
only exercises a single rate-limit bucket. In production, behind a
reverse proxy, `RateLimiter` needs to read the real client IP from
`X-Forwarded-For` (tracked in
`docs/decisions/005-queue-and-rate-limiting.md`), otherwise every
visitor would share one bucket — the opposite problem from what we
see in this local test.
