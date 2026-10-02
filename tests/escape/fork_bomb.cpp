#include <unistd.h>
int main() {
    for (;;) {
        if (fork() < 0) {
            // pids-limit je pogođen; fork() ne uspeva -> izađi odmah
            return 1;
        }
    }
}