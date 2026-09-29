# Worker pool, ograničen red i rate limiting po IP-u

## Kontekst

Do Dana 5, svaki HTTP zahtev je odmah pokretao Docker kontejnere bez
ikakvog ograničenja. Veliki broj istovremenih zahteva bi pokrenuo
jednako mnogo paralelnih kompajliranja/izvršavanja, što bi moglo da
preoptereti server (CPU, memorija, broj Docker procesa), a jedan
korisnik je mogao neograničeno da zauzima resurse.

## Odluka

- `RunQueue`: `ThreadPoolExecutor` sa 4 fiksna radnika i ograničenim
  redom od 20 mesta (`ArrayBlockingQueue`). Kad su radnici zauzeti i
  red pun, novi posao se odmah odbija (`QueueFullException`), umesto
  da čeka neograničeno.
- `RateLimiter`: token bucket po IP-u (`getRemoteAddr()`), kapacitet
  10 tokena, punjenje 10 tokena u minuti, u memoriji
  (`ConcurrentHashMap`), bez spoljne zavisnosti poput Redisa.

Oba mehanizma su namerno jednostavna, jer server radi na jednoj
instanci; distribuirani rate limiting (Redis) bi bio potreban samo
ako bi server ikad radio na više mašina istovremeno.

## Provera

Ručni test sa 50 paralelnih zahteva sa istog izvora: tačno 10 je
prošlo (`200`), tačno 40 odbijeno (`429`), što se poklapa sa
kapacitetom kofe.

## Poznata ograničenja

- `RateLimiter` kofe se nikad ne brišu iz mape; za mali broj
  jedinstvenih IP-ova je to zanemarljivo, ali bi kod ozbiljnijeg
  saobraćaja trebalo dodati isterivanje starih unosa (TTL).
- `getRemoteAddr()` u razvoju vraća `127.0.0.1` za sve zahteve. Iza
  reverse proxy-ja (Caddy, planirano za Dan 8), ovo mora da čita
  `X-Forwarded-For` zaglavlje, inače svi zahtevi dele istu kofu.
