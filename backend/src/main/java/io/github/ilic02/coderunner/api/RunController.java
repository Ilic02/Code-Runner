package io.github.ilic02.coderunner.api;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.ilic02.coderunner.queue.RateLimiter;
import io.github.ilic02.coderunner.queue.RunQueue;
import io.github.ilic02.coderunner.sandbox.DockerRunner;
import io.github.ilic02.coderunner.sandbox.LanguageConfig;
import io.github.ilic02.coderunner.sandbox.RunResult;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class RunController {

    private static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_STDIN_BYTES = 64 * 1024;

    private final DockerRunner runner;
    private final RunQueue queue;
    private final RateLimiter rateLimiter;

    public RunController(DockerRunner runner, RunQueue queue, RateLimiter rateLimiter) {
        this.runner = runner;
        this.queue = queue;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/runs")
    public RunResult run(@RequestBody RunRequest request, HttpServletRequest httpRequest) {
        String clientId = httpRequest.getRemoteAddr();
        if (!rateLimiter.allow(clientId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded");
        }

        if (request.language() == null || request.language().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "language is required");
        }
        if (request.source() == null || request.source().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source is required");
        }

        String stdin = request.stdin() == null ? "" : request.stdin();

        if (byteLength(request.source()) > MAX_SOURCE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "source is too large");
        }
        if (byteLength(stdin) > MAX_STDIN_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "stdin is too large");
        }

        LanguageConfig lang;
        try {
            lang = LanguageConfig.byId(request.language());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported language");
        }

        try {
            return queue.submit(() -> runner.run(lang, request.source(), stdin)).get();
        } catch (RunQueue.QueueFullException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "execution failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "interrupted");
        }
    }

    private static int byteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}