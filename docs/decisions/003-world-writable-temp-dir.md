# Privremeni folder mora biti writable za sve (world-writable)

## Kontekst

Privremeni folder za svaki pokrenuti kod je pravljen sa dozvolama `755`
(rwxr-xr-x), a kontejner (korisnik `runner`, UID 1000 iz Dockerfile-a)
mora da upiše `a.out` u njega tokom kompajliranja.

## Problem

Lokalno je sve radilo, jer je prvi korisnik na Ubuntuu obično UID 1000,
isti kao `runner` u kontejneru — pa je kontejner efektivno pisao kao
"vlasnik" foldera. Na GitHub Actions runneru host korisnik ima drugi
UID (1001), pa je kontejner upao u kategoriju "ostali" bez prava
pisanja, i kompajliranje je redovno padalo sa `COMPILE_ERROR` iako je
izvorni kod bio ispravan.

## Odluka

Privremeni folder se pravi sa dozvolama `777` (rwxrwxrwx). Bezbedno je
u ovom kontekstu: folder ima nasumično ime, postoji samo tokom jednog
poziva i briše se u `finally` bloku odmah posle izvršavanja.

## Posledica

Kod radi identično bez obzira na UID host korisnika koji pokreće
backend, uključujući CI runnere.
