package io.github.ilic02.coderunner.api;

public record RunRequest(String language, String source, String stdin) {}