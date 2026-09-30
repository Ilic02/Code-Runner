package io.github.ilic02.coderunner.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class SnippetRepositoryTest {

    @TempDir
    Path tempDir;

    private SnippetRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("test.db"));
        repository = new SnippetRepository(dataSource);
    }

    @Test
    void savedSnippetCanBeReadBack() {
        String id = repository.save("cpp", "int main(){}", "1 2");

        var found = repository.findById(id);

        assertTrue(found.isPresent());
        assertEquals("cpp", found.get().language());
        assertEquals("int main(){}", found.get().source());
        assertEquals("1 2", found.get().stdin());
        assertNotNull(found.get().createdAt());
    }

    @Test
    void unknownIdReturnsEmpty() {
        assertTrue(repository.findById("doesnotexist").isEmpty());
    }

    @Test
    void idsAreEightCharsAndDistinctAcrossSaves() {
        String id1 = repository.save("cpp", "a", "");
        String id2 = repository.save("cpp", "b", "");

        assertEquals(8, id1.length());
        assertEquals(8, id2.length());
        assertNotEquals(id1, id2);
    }
}