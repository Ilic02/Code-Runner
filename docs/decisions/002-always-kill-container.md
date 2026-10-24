# Uvek gasi kontejner posle waitFor, ne samo na timeout

## Kontekst

Tajmer za vremenski limit (`killTask`) je zakazan da pozove `docker kill`
tek kad istekne `RUN_TIMEOUT_MS`. Ako proces u kontejneru završi ranije
(normalno, greškom, ili ga ubije OOM), `killTask` se otkazuje
(`killTask.cancel(false)`) i `killContainer` se nikad ne pozove.

## Problem

Testovi su povremeno ostavljali kontejnere da rade u pozadini
("Up 15 minutes" u `docker ps -a`), iako je Java proces (`process.waitFor()`)
već vratio rezultat. Dijagnostikom (debug ispisi oko `waitFor`, `killTask`
i `killContainer`) potvrđeno je da `waitFor()` ume da se vrati (npr.
exitCode 1 kod `huge_output.cpp`) a da kontejner sam po sebi ostane aktivan
duže — verovatno zbog stanja cevi (pipe) koja se puni dok se čita samo do
`OUTPUT_LIMIT_BYTES`, ili nekog drugog neuobičajenog završetka `docker run`
klijenta nezavisno od stvarnog stanja kontejnera.

## Odluka

`killContainer(containerName)` se poziva bezuslovno u `finally` bloku,
odmah posle `waitFor()`, nezavisno od uzroka završetka. `docker kill` na
kontejner koji je već uklonjen (zbog `--rm`) samo vrati grešku koja se
tiho ignoriše, pa je ovaj poziv jeftin i bezbedan u svim slučajevima.

## Posledica

Dodatni `docker kill` poziv po svakom pokretanju (i za kompajliranje i za
izvršavanje), što je zanemarljiv trošak u odnosu na garanciju da ne
ostaju zaglavljeni kontejneri.