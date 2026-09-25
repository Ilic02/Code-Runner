#include <cstdlib>
#include <iostream>
int main() {
    int rc = system("wget -T 2 -q -O- http://example.com > /dev/null 2>&1");
    std::cout << (rc == 0 ? "CONNECTED" : "BLOCKED");
}