package io.github.ilic02.coderunner.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.web.server.ResponseStatusException;

import io.github.ilic02.coderunner.storage.SnippetRepository;

class SnippetControllerTest {

    @TempDir
    Path tempDir;

    private SnippetController controller;

    @BeforeEach
    void setUp() {
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        var repository = new SnippetRepository(dataSource);
        controller = new SnippetController(repository);
    }

    private int statusOf(Runnable action) {
        var ex = assertThrows(ResponseStatusException.class, action::run);
        return ex.getStatusCode().value();
    }

    @Test
    void savedSnippetCanBeRetrieved() {
        var saveResponse = controller.save(new SnippetRequest("java", "class Main {}", "1 2"));

        var snippet = controller.get(saveResponse.id());

        assertEquals("java", snippet.language());
        assertEquals("class Main {}", snippet.source());
        assertEquals("1 2", snippet.stdin());
    }

    @Test
    void missingLanguageGives400() {
        assertEquals(400, statusOf(() -> controller.save(new SnippetRequest(null, "int main(){}", ""))));
    }

    @Test
    void unknownLanguageGives400() {
        assertEquals(400, statusOf(() -> controller.save(new SnippetRequest("cobol", "print(1)", ""))));
    }

    @Test
    void blankSourceGives400() {
        assertEquals(400, statusOf(() -> controller.save(new SnippetRequest("cpp", "   ", ""))));
    }

    @Test
    void tooLargeSourceGives413() {
        String huge = "x".repeat(64 * 1024 + 1);
        assertEquals(413, statusOf(() -> controller.save(new SnippetRequest("cpp", huge, ""))));
    }

    @Test
    void unknownIdGives404() {
        assertEquals(404, statusOf(() -> controller.get("doesnotexist")));
    }

    @Test
    void nullStdinIsStoredAsEmptyString() {
        var saveResponse = controller.save(new SnippetRequest("cpp", "int main(){}", null));
        var snippet = controller.get(saveResponse.id());
        assertEquals("", snippet.stdin());
    }
}