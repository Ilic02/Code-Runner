#include <cstdlib>
int main() {
    for (;;) {
        volatile char* p = (volatile char*)malloc(1 << 20);
        for (int i = 0; i < (1 << 20); i++) p[i] = 1;
    }
}