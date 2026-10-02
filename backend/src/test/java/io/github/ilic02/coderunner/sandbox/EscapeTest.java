package io.github.ilic02.coderunner.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class EscapeTest {

    private final DockerRunner runner = new DockerRunner();
    private static final Path ESCAPE_DIR = Path.of("../tests/escape");

    private String read(String filename) throws Exception {
        return Files.readString(ESCAPE_DIR.resolve(filename));
    }

    @Test
    @Timeout(15)
    void infiniteLoopIsStoppedByTimeLimit() throws Exception {
        var result = runner.run(LanguageConfig.CPP, read("infinite_loop.cpp"), "");
        assertEquals(Verdict.TIME_LIMIT, result.verdict());
    }

    @Test
    @Timeout(15)
    void memoryHogIsStoppedByMemoryLimit() throws Exception {
        var result = runner.run(LanguageConfig.CPP, read("memory_hog.cpp"), "");
        assertEquals(Verdict.MEMORY_LIMIT, result.verdict());
    }

    @Test
    @Timeout(15)
    void forkBombIsStoppedByPidsLimit() throws Exception {
        var result = runner.run(LanguageConfig.CPP, read("fork_bomb.cpp"), "");
        // ne sme da obori mašinu niti da radi zauvek; ili padne na pids limitu (RUNTIME_ERROR)
        // ili istekne vremenski limit pre nego što se otme kontroli
        assertEquals(Verdict.RUNTIME_ERROR, result.verdict());
    }

    @Test
    @Timeout(15)
    void hugeOutputIsTruncated() throws Exception {
        var result = runner.run(LanguageConfig.CPP, read("huge_output.cpp"), "");
        assertEquals(Verdict.OUTPUT_LIMIT, result.verdict());
    }

    @Test
    void networkCallIsBlocked() throws Exception {
        var result = runner.run(LanguageConfig.CPP, read("network_call.cpp"), "");
        assertEquals("BLOCKED", result.stdout());
    }

    @Test
    void diskWriteOutsideTmpIsBlocked() throws Exception {
        var result = runner.run(LanguageConfig.CPP, read("write_disk.cpp"), "");
        assertEquals("BLOCKED", result.stdout());
    }
}