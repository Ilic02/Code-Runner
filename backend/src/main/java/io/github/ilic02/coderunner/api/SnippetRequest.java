package io.github.ilic02.coderunner.api;

public record SnippetRequest(String language, String source, String stdin) {}