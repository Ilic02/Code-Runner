package io.github.ilic02.coderunner.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import io.github.ilic02.coderunner.sandbox.DockerRunner;
import io.github.ilic02.coderunner.sandbox.LanguageConfig;
import io.github.ilic02.coderunner.sandbox.RunResult;
import io.github.ilic02.coderunner.sandbox.Verdict;

class RunControllerTest {

    // lažni runner: ne pokreće Docker, samo pamti šta je dobio
    private static class StubRunner extends DockerRunner {
        int calls;
        LanguageConfig lastLang;
        String lastSource;
        String lastStdin;

        @Override
        public RunResult run(LanguageConfig lang, String source, String stdin) {
            calls++;
            lastLang = lang;
            lastSource = source;
            lastStdin = stdin;
            return new RunResult(Verdict.OK, 0, "out", "", 7);
        }
    }

    private final StubRunner runner = new StubRunner();
    private final RunController controller = new RunController(runner);

    private int statusOf(RunRequest request) {
        var ex = assertThrows(ResponseStatusException.class, () -> controller.run(request));
        return ex.getStatusCode().value();
    }

    @Test
    void validRequestIsDelegatedWithSelectedLanguage() throws Exception {
        var result = controller.run(new RunRequest("java", "class Main {}", "1 2"));

        assertEquals(Verdict.OK, result.verdict());
        assertEquals(1, runner.calls);
        assertSame(LanguageConfig.JAVA, runner.lastLang);
        assertEquals("class Main {}", runner.lastSource);
        assertEquals("1 2", runner.lastStdin);
    }

    @Test
    void nullStdinBecomesEmptyString() throws Exception {
        controller.run(new RunRequest("cpp", "int main() {}", null));
        assertEquals("", runner.lastStdin);
    }

    @Test
    void unknownLanguageGives400() {
        assertEquals(400, statusOf(new RunRequest("python", "print(1)", "")));
        assertEquals(0, runner.calls);
    }

    @Test
    void missingLanguageGives400() {
        assertEquals(400, statusOf(new RunRequest(null, "int main() {}", "")));
        assertEquals(0, runner.calls);
    }

    @Test
    void blankSourceGives400() {
        assertEquals(400, statusOf(new RunRequest("cpp", "   ", "")));
        assertEquals(0, runner.calls);
    }

    @Test
    void tooLargeSourceGives413() {
        String huge = "x".repeat(64 * 1024 + 1);
        assertEquals(413, statusOf(new RunRequest("cpp", huge, "")));
        assertEquals(0, runner.calls);
    }

    @Test
    void tooLargeStdinGives413() {
        String huge = "x".repeat(64 * 1024 + 1);
        assertEquals(413, statusOf(new RunRequest("cpp", "int main() {}", huge)));
        assertEquals(0, runner.calls);
    }

    @Test
    void limitIsMeasuredInBytesNotCharacters() {
        // 40 000 karaktera, ali 80 000 bajtova u UTF-8 (svako "č" zauzima 2 bajta)
        String multibyte = "č".repeat(40_000);
        assertEquals(413, statusOf(new RunRequest("cpp", multibyte, "")));
    }
}