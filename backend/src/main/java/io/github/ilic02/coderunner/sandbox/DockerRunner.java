package io.github.ilic02.coderunner.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class DockerRunner {

    private static final long COMPILE_TIMEOUT_MS = 10_000;
    private static final long RUN_TIMEOUT_MS = 5_000;
    private static final long OUTPUT_LIMIT_BYTES = 64 * 1024; // 64 KB
    private static final String MEMORY_LIMIT = "128m";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

    public RunResult run(LanguageConfig lang, String source, String stdin)
            throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory("run-");
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"));
            Path file = dir.resolve(lang.sourceFileName());
            Files.writeString(file, source);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

            // Faza 1: kompajliranje, bez ograničenja resursa, samo razumno vreme
            String compileContainer = "compile-" + UUID.randomUUID();
            List<String> compileCmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--name", compileContainer,
                "-v", dir + ":/work:rw",
                lang.image()));
            compileCmd.addAll(lang.compileCommand());

            long compileStart = System.currentTimeMillis();
            ExecOutcome compileOutcome = runWithTimeout(compileCmd, "", compileContainer, COMPILE_TIMEOUT_MS);
            long compileTime = System.currentTimeMillis() - compileStart;

            if (compileOutcome.timedOut() || compileOutcome.exitCode() != 0) {
                Verdict v = compileOutcome.timedOut() ? Verdict.TIME_LIMIT : Verdict.COMPILE_ERROR;
                return new RunResult(v, compileOutcome.exitCode(), compileOutcome.stdout(),
                        compileOutcome.stderr(), compileTime);
            }

            // Faza 2: izvršavanje, sa punim skupom limita
            String runContainer = "run-" + UUID.randomUUID();
            List<String> runCmd = new ArrayList<>(List.of(
                "docker", "run", "--rm", "-i", "--name", runContainer,
                "-v", dir + ":/work:ro",
                "--memory", MEMORY_LIMIT, "--memory-swap", MEMORY_LIMIT,
                "--pids-limit", "64",
                "--network", "none",
                "--read-only", "--tmpfs", "/tmp:size=16m",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
                "--user", "1000:1000",
                lang.image()));
            runCmd.addAll(lang.runCommand());

            long runStart = System.currentTimeMillis();
            ExecOutcome runOutcome = runWithTimeout(runCmd, stdin, runContainer, RUN_TIMEOUT_MS);
            long runTime = System.currentTimeMillis() - runStart;

            Verdict verdict = classify(runOutcome);
            return new RunResult(verdict, runOutcome.exitCode(), runOutcome.stdout(),
                    runOutcome.stderr(), runTime);
        } finally {
            deleteRecursively(dir);
        }
    }

    private Verdict classify(ExecOutcome outcome) {
        if (outcome.timedOut()) return Verdict.TIME_LIMIT;
        if (outcome.outputTruncated()) return Verdict.OUTPUT_LIMIT;
        if (outcome.exitCode() == 137) return Verdict.MEMORY_LIMIT;
        if (outcome.exitCode() == 0) return Verdict.OK;
        return Verdict.RUNTIME_ERROR;
    }

    private record ExecOutcome(int exitCode, String stdout, String stderr,
                                boolean timedOut, boolean outputTruncated) {}

    private ExecOutcome runWithTimeout(List<String> cmd, String stdin, String containerName, long timeoutMs)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd).start();
        AtomicBoolean truncated = new AtomicBoolean(false);

        CompletableFuture<String> out = readLimited(process.getInputStream(), truncated);
        CompletableFuture<String> err = readLimited(process.getErrorStream(), truncated);

        try (OutputStream in = process.getOutputStream()) {
            in.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // proces je možda završio pre nego što je pročitao ulaz
        }

        AtomicBoolean timedOut = new AtomicBoolean(false);
        var killTask = scheduler.schedule(() -> {
            timedOut.set(true);
            killContainer(containerName);
        }, timeoutMs, TimeUnit.MILLISECONDS);

        var watchTask = scheduler.scheduleAtFixedRate(() -> {
            if (truncated.get()) killContainer(containerName);
        }, 200, 200, TimeUnit.MILLISECONDS);

        int exitCode;
        try {
            exitCode = process.waitFor();
        } finally {
            killTask.cancel(false);
            watchTask.cancel(false);
            killContainer(containerName); // uvek pokušaj da ugasiš, za svaki slučaj
        }

        return new ExecOutcome(exitCode, out.join(), err.join(), timedOut.get(), truncated.get());
    }

    private void killContainer(String name) {
        try {
            new ProcessBuilder("docker", "kill", name)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        } catch (IOException ignored) {
            // kontejner je možda već završio sam
        }
    }

    private static CompletableFuture<String> readLimited(InputStream stream, AtomicBoolean truncated) {
        return CompletableFuture.supplyAsync(() -> {
            var buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            try (stream) {
                int n;
                while ((n = stream.read(chunk)) != -1) {
                    if (buffer.size() + n > OUTPUT_LIMIT_BYTES) {
                        buffer.write(chunk, 0, (int) (OUTPUT_LIMIT_BYTES - buffer.size()));
                        truncated.set(true);
                        break;
                    }
                    buffer.write(chunk, 0, n);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        });
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}