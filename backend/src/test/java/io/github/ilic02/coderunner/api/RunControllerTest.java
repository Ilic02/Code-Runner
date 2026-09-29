package io.github.ilic02.coderunner.api;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import io.github.ilic02.coderunner.queue.RateLimiter;
import io.github.ilic02.coderunner.queue.RunQueue;
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
    private final RunQueue queue = new RunQueue();
    private final RateLimiter rateLimiter = new RateLimiter();
    private final RunController controller = new RunController(runner, queue, rateLimiter);

    // testovi u ovoj klasi koriste svoj IP po testu, da im rate limiter ne smeta jedan drugom
    private static HttpServletRequest fakeRequest(String ip) {
        return (HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(),
            new Class<?>[]{HttpServletRequest.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getRemoteAddr")) return ip;
                return null;
            });
    }

    private int statusOf(RunRequest request, String ip) {
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.run(request, fakeRequest(ip)));
        return ex.getStatusCode().value();
    }

    @Test
    void validRequestIsDelegatedWithSelectedLanguage() throws Exception {
        var result = controller.run(new RunRequest("java", "class Main {}", "1 2"), fakeRequest("1.1.1.1"));

        assertEquals(Verdict.OK, result.verdict());
        assertEquals(1, runner.calls);
        assertSame(LanguageConfig.JAVA, runner.lastLang);
        assertEquals("class Main {}", runner.lastSource);
        assertEquals("1 2", runner.lastStdin);
    }

    @Test
    void nullStdinBecomesEmptyString() throws Exception {
        controller.run(new RunRequest("cpp", "int main() {}", null), fakeRequest("1.1.1.2"));
        assertEquals("", runner.lastStdin);
    }

    @Test
    void unknownLanguageGives400() {
        assertEquals(400, statusOf(new RunRequest("python", "print(1)", ""), "1.1.1.3"));
        assertEquals(0, runner.calls);
    }

    @Test
    void missingLanguageGives400() {
        assertEquals(400, statusOf(new RunRequest(null, "int main() {}", ""), "1.1.1.4"));
        assertEquals(0, runner.calls);
    }

    @Test
    void blankSourceGives400() {
        assertEquals(400, statusOf(new RunRequest("cpp", "   ", ""), "1.1.1.5"));
        assertEquals(0, runner.calls);
    }

    @Test
    void tooLargeSourceGives413() {
        String huge = "x".repeat(64 * 1024 + 1);
        assertEquals(413, statusOf(new RunRequest("cpp", huge, ""), "1.1.1.6"));
        assertEquals(0, runner.calls);
    }

    @Test
    void tooLargeStdinGives413() {
        String huge = "x".repeat(64 * 1024 + 1);
        assertEquals(413, statusOf(new RunRequest("cpp", "int main() {}", huge), "1.1.1.7"));
        assertEquals(0, runner.calls);
    }

    @Test
    void limitIsMeasuredInBytesNotCharacters() {
        String multibyte = "č".repeat(40_000);
        assertEquals(413, statusOf(new RunRequest("cpp", multibyte, ""), "1.1.1.8"));
    }

    @Test
    void rateLimitKicksInAfterBucketIsExhausted() throws Exception {
        String ip = "9.9.9.9";
        // kofa ima 10 tokena; prvih 10 zahteva prolazi
        for (int i = 0; i < 10; i++) {
            controller.run(new RunRequest("cpp", "int main() {}", ""), fakeRequest(ip));
        }
        // 11. zahtev istog IP-a u kratkom vremenskom razmaku dobija 429
        assertEquals(429, statusOf(new RunRequest("cpp", "int main() {}", ""), ip));
    }

    @Test
    void differentIpsHaveSeparateBuckets() throws Exception {
        // IP A troši svih 10 tokena
        for (int i = 0; i < 10; i++) {
            controller.run(new RunRequest("cpp", "int main() {}", ""), fakeRequest("2.2.2.2"));
        }
        // IP B i dalje ima svoju punu kofu
        var result = controller.run(new RunRequest("cpp", "int main() {}", ""), fakeRequest("3.3.3.3"));
        assertEquals(Verdict.OK, result.verdict());
    }
}