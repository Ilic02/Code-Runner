# Ne oslanjati se na fiksan UID kontejnera

## Kontekst

Faza izvršavanja je koristila `--user 1000:1000`, pretpostavljajući da
korisnik `runner` (napravljen sa `useradd -m runner` u svakom
Dockerfile-u) uvek dobija UID 1000.

## Problem

`useradd` dodeljuje prvi slobodan UID, koji zavisi od baznih slika.
U `gcc:14` (Debian) `runner` je UID 1000, a u
`eclipse-temurin:25-jdk` je UID 1001 (drugi skup unapred postojećih
naloga). Fiksan `--user 1000:1000` bi značio da Java kontejner radi
kao pogrešan/nepostojeći korisnik.

## Odluka

Ukloniti `--user 1000:1000` iz DockerRunner-a i osloniti se na
`USER runner` iz svakog Dockerfile-a, koji već obezbeđuje da proces
ne radi kao root, bez obzira na tačan UID. Ovo je bezbedno jer je
privremeni folder world-writable (vidi 003), pa tačan UID više nije
bitan za pristup fajlovima.

## Posledica

Svaka runner slika može imati svoj UID za `runner` korisnika, bez
potrebe za usklađivanjem.