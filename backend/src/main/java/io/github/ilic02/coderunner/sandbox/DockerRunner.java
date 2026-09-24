package io.github.ilic02.coderunner.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class DockerRunner {

    public record RunResult(int exitCode, String stdout, String stderr) {}

    public RunResult runCpp(String source, String stdin)
            throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("run-");
        try {
            // kontejner radi kao drugi korisnik, pa fajlovi moraju biti čitljivi svima
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
            Path file = dir.resolve("main.cpp");
            Files.writeString(file, source);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

            List<String> cmd = List.of(
                "docker", "run", "--rm", "-i",
                "-v", dir + ":/work:ro",
                "coderunner/cpp",
                "sh", "-c", "g++ -O2 -o /tmp/a.out /work/main.cpp && /tmp/a.out");

            Process process = new ProcessBuilder(cmd).start();
            CompletableFuture<String> out = readAsync(process.getInputStream());
            CompletableFuture<String> err = readAsync(process.getErrorStream());

            try (OutputStream in = process.getOutputStream()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // program je možda završio pre nego što je pročitao ulaz
            }

            int exitCode = process.waitFor();
            return new RunResult(exitCode, out.join(), err.join());
        } finally {
            deleteRecursively(dir);
        }
    }

    // stdout i stderr se čitaju istovremeno, inače proces može da se zaglavi
    // kad se jedan od bafera napuni
    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}