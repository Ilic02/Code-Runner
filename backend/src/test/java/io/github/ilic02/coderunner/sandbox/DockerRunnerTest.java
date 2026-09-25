package io.github.ilic02.coderunner.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class DockerRunnerTest {

    private final DockerRunner runner = new DockerRunner();

    @Test
    void helloWorld() throws Exception {
        var result = runner.runCpp("""
            #include <iostream>
            int main() { std::cout << "Hello"; }
            """, "");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals(0, result.exitCode());
        assertEquals("Hello", result.stdout());
    }

    @Test
    void readsStdin() throws Exception {
        var result = runner.runCpp("""
            #include <iostream>
            int main() { int a, b; std::cin >> a >> b; std::cout << a + b; }
            """, "2 3");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("5", result.stdout());
    }

    @Test
    void compileErrorGivesCompileErrorVerdict() throws Exception {
        var result = runner.runCpp("int main( {", "");
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("error"));
    }

    @Test
    void runtimeExitCodeGivesRuntimeError() throws Exception {
        var result = runner.runCpp("int main() { return 3; }", "");
        assertEquals(Verdict.RUNTIME_ERROR, result.verdict());
        assertEquals(3, result.exitCode());
    }

    @Test
    @Timeout(15) // testni okvir kao dodatna zaštita ako bi nadzor u kodu ikad zakazao
    void infiniteLoopGivesTimeLimit() throws Exception {
        var result = runner.runCpp("int main() { for (;;) {} }", "");
        assertEquals(Verdict.TIME_LIMIT, result.verdict());
    }

    @Test
    @Timeout(15)
    void memoryHogGivesMemoryLimit() throws Exception {
        var result = runner.runCpp("""
            #include <cstdlib>
            int main() {
                for (;;) {
                    volatile char* p = (volatile char*)malloc(1 << 20);
                    for (int i = 0; i < (1 << 20); i++) p[i] = 1;
                }
            }
            """, "");
        assertEquals(Verdict.MEMORY_LIMIT, result.verdict());
    }

    @Test
    @Timeout(15)
    void hugeOutputGivesOutputLimit() throws Exception {
        var result = runner.runCpp("""
            #include <iostream>
            int main() { for (;;) std::cout << "x"; }
            """, "");
        assertEquals(Verdict.OUTPUT_LIMIT, result.verdict());
    }

    @Test
    void networkCallFailsInsideSandbox() throws Exception {
        var result = runner.runCpp("""
            #include <cstdlib>
            #include <iostream>
            int main() {
                int rc = system("wget -T 2 -q -O- http://example.com > /dev/null 2>&1");
                std::cout << (rc == 0 ? "CONNECTED" : "BLOCKED");
            }
            """, "");
        assertEquals("BLOCKED", result.stdout());
    }
}