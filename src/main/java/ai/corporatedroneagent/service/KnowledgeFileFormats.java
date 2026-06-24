package ai.corporatedroneagent.service;

import java.util.Locale;
import java.util.Set;

/**
 * Central classification of the local-file extensions the ingestion pipeline understands.
 * Two kinds matter downstream: <em>text</em> formats decode straight to UTF-8, while
 * <em>document</em> formats need binary extraction (Tika). The read stage uses this to gate
 * formats and pick a size cap; the conversion stage uses it to route to the right renderer.
 */
public final class KnowledgeFileFormats {

    private static final Set<String> TEXT_FORMATS = Set.of(
            "adoc",
            "asciidoc",
            "bash",
            "bat",
            "c",
            "cc",
            "cfg",
            "cjs",
            "cmd",
            "conf",
            "cpp",
            "cs",
            "css",
            "csv",
            "dart",
            "env",
            "go",
            "gradle",
            "graphql",
            "gql",
            "groovy",
            "h",
            "hpp",
            "htm",
            "html",
            "ini",
            "java",
            "js",
            "json",
            "jsx",
            "kt",
            "kts",
            "less",
            "log",
            "lua",
            "markdown",
            "md",
            "mjs",
            "php",
            "pl",
            "pm",
            "properties",
            "proto",
            "ps1",
            "py",
            "r",
            "rb",
            "rs",
            "rst",
            "sass",
            "scala",
            "scss",
            "sh",
            "sql",
            "svelte",
            "swift",
            "tex",
            "toml",
            "ts",
            "tsv",
            "tsx",
            "txt",
            "vue",
            "xml",
            "yaml",
            "yml",
            "zsh"
    );

    private static final Set<String> DOCUMENT_FORMATS = Set.of(
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "ppt",
            "pptx",
            "odt",
            "ods",
            "odp",
            "rtf"
    );

    private KnowledgeFileFormats() {
    }

    /** A plain-text format that decodes directly to UTF-8 text. */
    public static boolean isText(String format) {
        return TEXT_FORMATS.contains(normalize(format));
    }

    /** A binary document format that needs extraction (PDF, Office, OpenDocument, RTF). */
    public static boolean isDocument(String format) {
        return DOCUMENT_FORMATS.contains(normalize(format));
    }

    /** Whether the pipeline can ingest this format at all. */
    public static boolean isSupported(String format) {
        return isText(format) || isDocument(format);
    }

    private static String normalize(String format) {
        return format == null ? "" : format.toLowerCase(Locale.ROOT);
    }
}
