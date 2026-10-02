#!/bin/bash
# Meri vreme jednog zahteva za dati jezik. Koristi RunResult.timeMs (iz tela
# odgovora, meri samo fazu kompajliranja+izvršavanja) i ukupno HTTP vreme
# (curl, meri i mrežu/red/validaciju) radi poređenja.

run_once() {
  local lang=$1
  local source=$2
  curl -s -w "\nhttp_total=%{time_total}s\n" -X POST http://localhost:8080/api/runs \
    -H "Content-Type: application/json" \
    -d "{\"language\":\"$lang\",\"source\":\"$source\",\"stdin\":\"\"}"
}

echo "=== C++ ==="
run_once cpp '#include <iostream>\nint main(){std::cout<<\"Hello\";}'
echo "=== C ==="
run_once c '#include <stdio.h>\nint main(){printf(\"Hello\");}'
echo "=== Java ==="
run_once java 'public class Main{public static void main(String[] a){System.out.print(\"Hello\");}}'
echo "=== Python ==="
run_once python 'print(\"Hello\")'
echo "=== JavaScript ==="
run_once javascript 'console.log(\"Hello\")'
