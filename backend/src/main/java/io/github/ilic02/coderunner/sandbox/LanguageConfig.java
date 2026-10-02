package io.github.ilic02.coderunner.sandbox;

import java.util.List;

public record LanguageConfig(
        String id,
        String image,
        String sourceFileName,
        List<String> compileCommand,
        List<String> runCommand
) {
    public static final LanguageConfig CPP = new LanguageConfig(
            "cpp",
            "coderunner/cpp",
            "main.cpp",
            List.of("g++", "-O2", "-o", "/work/a.out", "/work/main.cpp"),
            List.of("/work/a.out")
    );

    public static final LanguageConfig C = new LanguageConfig(
            "c",
            "coderunner/c",
            "main.c",
            List.of("gcc", "-O2", "-o", "/work/a.out", "/work/main.c"),
            List.of("/work/a.out")
    );

    public static final LanguageConfig JAVA = new LanguageConfig(
            "java",
            "coderunner/java",
            "Main.java",
            List.of("javac", "-d", "/work", "/work/Main.java"),
            List.of("java", "-cp", "/work", "Main")
    );

    public static final LanguageConfig PYTHON = new LanguageConfig(
            "python",
            "coderunner/python",
            "main.py",
            List.of("python3", "-m", "py_compile", "/work/main.py"),
            List.of("python3", "/work/main.py")
);

    public static final LanguageConfig JAVASCRIPT = new LanguageConfig(
            "javascript",
            "coderunner/js",
            "main.js",
            List.of("node", "--check", "/work/main.js"),
            List.of("node", "/work/main.js")
    );

    public static LanguageConfig byId(String id) {
        return switch (id) {
            case "cpp" -> CPP;
            case "c" -> C;
            case "java" -> JAVA;
            case "python" -> PYTHON;
            case "javascript" -> JAVASCRIPT;
            default -> throw new IllegalArgumentException("Unknown language: " + id);
        };
    }
}