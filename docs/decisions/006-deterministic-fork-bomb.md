# fork_bomb.cpp mora eksplicitno da proveri fork() grešku

## Kontekst

tests/escape/fork_bomb.cpp je pisan kao `for (;;) fork();`, ne
proveravajući povratnu vrednost fork() poziva.

## Problem

Kad --pids-limit (64) bude dostignut, fork() počinje da vraća -1
umesto da pravi nove procese. Neproveren povratni kod znači da
petlja jednostavno nastavlja da se vrti bez rasta broja procesa i
bez pada, pa program preživljava do isteka vremenskog limita.
Ishod je zavisio od finog tajminga (RUNTIME_ERROR ponekad, TIME_LIMIT
ponekad, jednom čak i drugačiji verdikt), što je testEscapeTest >
forkBombIsStoppedByPidsLimit() činilo povremeno nestabilnim (flaky).

## Odluka

fork_bomb.cpp sada proverava povratnu vrednost i odmah se gasi
(return 1) čim fork() ne uspe, umesto da nastavlja petlju. Test sad
očekuje isključivo Verdict.RUNTIME_ERROR, potvrđeno stabilno kroz
5 uzastopnih pokretanja.

## Posledica

Escape test za pids-limit je sada deterministički i brži (ne čeka
vremenski limit), a i realističnije predstavlja kako bi napadač koji
naiđe na ograničenje broja procesa zapravo bio zaustavljen.
