package dev.quill.filter;

/** Why a message was stopped: the category, the rule that matched (for staff), and the text that matched. */
public record Verdict(String category, String rule, String matched) {
}
