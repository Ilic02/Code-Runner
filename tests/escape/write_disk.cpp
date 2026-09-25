#include <fstream>
#include <iostream>
int main() {
    std::ofstream f("/etc/pwned");
    f << "test";
    std::cout << (f.good() ? "WROTE" : "BLOCKED");
}