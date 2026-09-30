package io.github.ilic02.coderunner.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.ilic02.coderunner.sandbox.LanguageConfig;
import io.github.ilic02.coderunner.storage.SnippetRepository;

@RestController
@RequestMapping("/api")
public class SnippetController {

    private static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_STDIN_BYTES = 64 * 1024;

    private final SnippetRepository repository;

    public SnippetController(SnippetRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/snippets")
    public SnippetResponse save(@RequestBody SnippetRequest request) {
        if (request.language() == null || request.language().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "language is required");
        }
        if (request.source() == null || request.source().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source is required");
        }

        try {
            LanguageConfig.byId(request.language());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported language");
        }

        String stdin = request.stdin() == null ? "" : request.stdin();

        if (byteLength(request.source()) > MAX_SOURCE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "source is too large");
        }
        if (byteLength(stdin) > MAX_STDIN_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "stdin is too large");
        }

        String id = repository.save(request.language(), request.source(), stdin);
        return new SnippetResponse(id);
    }

    @GetMapping("/snippets/{id}")
    public SnippetRepository.Snippet get(@PathVariable String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "snippet not found"));
    }

    private static int byteLength(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}