package io.github.ilic02.coderunner.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class DockerRunnerTest {

    private final DockerRunner runner = new DockerRunner();

    @Test
    void helloWorld() throws Exception {
        var result = runner.run(LanguageConfig.CPP, """
            #include <iostream>
            int main() { std::cout << "Hello"; }
            """, "");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals(0, result.exitCode());
        assertEquals("Hello", result.stdout());
    }

    @Test
    void readsStdin() throws Exception {
        var result = runner.run(LanguageConfig.CPP, """
            #include <iostream>
            int main() { int a, b; std::cin >> a >> b; std::cout << a + b; }
            """, "2 3");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("5", result.stdout());
    }

    @Test
    void compileErrorGivesCompileErrorVerdict() throws Exception {
        var result = runner.run(LanguageConfig.CPP, "int main( {", "");
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("error"));
    }

    @Test
    void runtimeExitCodeGivesRuntimeError() throws Exception {
        var result = runner.run(LanguageConfig.CPP, "int main() { return 3; }", "");
        assertEquals(Verdict.RUNTIME_ERROR, result.verdict());
        assertEquals(3, result.exitCode());
    }

    @Test
    @Timeout(15)
    void infiniteLoopGivesTimeLimit() throws Exception {
        var result = runner.run(LanguageConfig.CPP, "int main() { for (;;) {} }", "");
        assertEquals(Verdict.TIME_LIMIT, result.verdict());
    }

    @Test
    @Timeout(15)
    void memoryHogGivesMemoryLimit() throws Exception {
        var result = runner.run(LanguageConfig.CPP, """
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
        var result = runner.run(LanguageConfig.CPP, """
            #include <iostream>
            int main() { for (;;) std::cout << "x"; }
            """, "");
        assertEquals(Verdict.OUTPUT_LIMIT, result.verdict());
    }

    @Test
    void networkCallFailsInsideSandbox() throws Exception {
        var result = runner.run(LanguageConfig.CPP, """
            #include <cstdlib>
            #include <iostream>
            int main() {
                int rc = system("wget -T 2 -q -O- http://example.com > /dev/null 2>&1");
                std::cout << (rc == 0 ? "CONNECTED" : "BLOCKED");
            }
            """, "");
        assertEquals("BLOCKED", result.stdout());
    }

    @Test
    void cHelloWorld() throws Exception {
        var result = runner.run(LanguageConfig.C, """
            #include <stdio.h>
            int main() { printf("Hello from C"); return 0; }
            """, "");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("Hello from C", result.stdout());
    }

    @Test
    void cReadsStdin() throws Exception {
        var result = runner.run(LanguageConfig.C, """
            #include <stdio.h>
            int main() { int a, b; scanf("%d %d", &a, &b); printf("%d", a + b); return 0; }
            """, "2 3");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("5", result.stdout());
    }

    @Test
    void javaHelloWorld() throws Exception {
        var result = runner.run(LanguageConfig.JAVA, """
            public class Main {
                public static void main(String[] args) {
                    System.out.print("Hello from Java");
                }
            }
            """, "");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("Hello from Java", result.stdout());
    }

    @Test
    void javaReadsStdin() throws Exception {
        var result = runner.run(LanguageConfig.JAVA, """
            import java.util.Scanner;
            public class Main {
                public static void main(String[] args) {
                    Scanner sc = new Scanner(System.in);
                    int a = sc.nextInt(), b = sc.nextInt();
                    System.out.print(a + b);
                }
            }
            """, "2 3");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("5", result.stdout());
    }

    @Test
    void javaCompileErrorGivesCompileErrorVerdict() throws Exception {
        var result = runner.run(LanguageConfig.JAVA, "public class Main { public static void main(", "");
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
    }

    @Test
    void javaWrongClassNameGivesCompileError() throws Exception {
        // fajl je uvek "Main.java" (LanguageConfig.JAVA.sourceFileName()),
        // pa javna klasa sa drugim imenom mora da padne na kompajliranju
        var result = runner.run(LanguageConfig.JAVA, """
            public class Wrong {
                public static void main(String[] args) { System.out.println("x"); }
            }
            """, "");
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
    }

    @Test
    void pythonHelloWorld() throws Exception {
        var result = runner.run(LanguageConfig.PYTHON, """
            print("Hello from Python")
            """, "");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("Hello from Python\n", result.stdout());
    }

    @Test
    void pythonReadsStdin() throws Exception {
        var result = runner.run(LanguageConfig.PYTHON, """
            a, b = map(int, input().split())
            print(a + b)
            """, "2 3");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("5\n", result.stdout());
    }

    @Test
    void pythonSyntaxErrorGivesCompileErrorVerdict() throws Exception {
        var result = runner.run(LanguageConfig.PYTHON, "def broken(:\n    pass\n", "");
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
    }

    @Test
    void javascriptHelloWorld() throws Exception {
        var result = runner.run(LanguageConfig.JAVASCRIPT, """
            console.log("Hello from JS");
            """, "");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("Hello from JS\n", result.stdout());
    }

    @Test
    void javascriptReadsStdin() throws Exception {
        var result = runner.run(LanguageConfig.JAVASCRIPT, """
            const line = require('fs').readFileSync(0, 'utf-8').trim();
            const [a, b] = line.split(' ').map(Number);
            console.log(a + b);
            """, "2 3");
        assertEquals(Verdict.OK, result.verdict());
        assertEquals("5\n", result.stdout());
    }

    @Test
    void javascriptSyntaxErrorGivesCompileErrorVerdict() throws Exception {
        var result = runner.run(LanguageConfig.JAVASCRIPT, "function broken( {\n", "");
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
    }
}