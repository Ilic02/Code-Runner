package io.github.ilic02.coderunner.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DockerRunnerTest {

    private final DockerRunner runner = new DockerRunner();

    @Test
    void helloWorld() throws Exception {
        var result = runner.runCpp("""
            #include <iostream>
            int main() { std::cout << "Hello"; }
            """, "");
        assertEquals(0, result.exitCode());
        assertEquals("Hello", result.stdout());
    }

    @Test
    void readsStdin() throws Exception {
        var result = runner.runCpp("""
            #include <iostream>
            int main() { int a, b; std::cin >> a >> b; std::cout << a + b; }
            """, "2 3");
        assertEquals(0, result.exitCode());
        assertEquals("5", result.stdout());
    }

    @Test
    void compileErrorGivesNonZeroExitAndMessage() throws Exception {
        var result = runner.runCpp("int main( {", "");
        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("error"));
    }

    @Test
    void runtimeExitCodeIsPropagated() throws Exception {
        var result = runner.runCpp("int main() { return 3; }", "");
        assertEquals(3, result.exitCode());
    }
}