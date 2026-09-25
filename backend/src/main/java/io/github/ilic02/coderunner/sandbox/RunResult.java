package io.github.ilic02.coderunner.sandbox;

public record RunResult(Verdict verdict, int exitCode, String stdout, String stderr, long timeMs) {}