package com.convivo.gastoscomunes.conventions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class GitCommitConventionTest {

    /**
     * Expresion regular oficial de pr-quality.yml y AGENTS.md §11.2:
     * Acepta Gitmoji (shortcode :emoji: o simbolo unicode), tipo convencional, scope opcional y asunto.
     */
    public static final Pattern GITMOJI_CONVENTIONAL_PATTERN = Pattern.compile(
            "^(?::\\w+:|[^\\w\\s:]\\S*)\\s(\\w*)(?:\\(([\\w$.\\-*/ ]*)\\))?!?:\\s(.*)$"
    );

    public static boolean isValidCommitMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String firstLine = message.lines().findFirst().orElse("").trim();
        Matcher matcher = GITMOJI_CONVENTIONAL_PATTERN.matcher(firstLine);
        if (!matcher.matches()) {
            return false;
        }
        String type = matcher.group(1);
        return switch (type) {
            case "feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore", "revert" -> true;
            default -> false;
        };
    }

    @ParameterizedTest(name = "Mensaje valido: {0}")
    @ValueSource(strings = {
            ":sparkles: feat: crear nuevo cobro",
            "✨ feat(cobros): calcular total prorrateado",
            ":lock: fix(deps): parchar cve de dependencia",
            ":memo: docs(readme): actualizar guia",
            ":construction_worker: ci: agregar senales de calidad",
            ":white_check_mark: test: agregar pruebas unitarias",
            ":wrench: chore(deps): actualizar dependencias",
            "🐛 fix(cuentas): resolver null pointer en calculo"
    })
    @DisplayName("Mensajes con Gitmoji y Conventional Commits deben ser aceptados")
    void validGitmojiCommitMessagesPass(String validMessage) {
        assertThat(isValidCommitMessage(validMessage))
                .as("El mensaje '%s' debe ser valido segun la convencion Gitmoji", validMessage)
                .isTrue();
    }

    @ParameterizedTest(name = "Mensaje invalido: {0}")
    @ValueSource(strings = {
            "fix: sin gitmoji",
            ":sparkles: mensaje sin tipo convencional",
            "WIP test sin formato",
            ":sparkles: invento: tipo no permitido en conventional commits",
            "feat(modulo): falta gitmoji",
            ""
    })
    @DisplayName("Mensajes que no cumplan Gitmoji + Conventional Commits deben fallar")
    void invalidCommitMessagesFail(String invalidMessage) {
        assertThat(isValidCommitMessage(invalidMessage))
                .as("El mensaje '%s' debe ser rechazado", invalidMessage)
                .isFalse();
    }

    @Test
    @DisplayName("Verificacion opcional de HEAD si -Dcheck.commit=true esta activo")
    void checkCurrentGitHeadCommitIfConfigured() {
        if (!Boolean.getBoolean("check.commit")) {
            return;
        }
        try {
            Process process = new ProcessBuilder("git", "log", "-1", "--pretty=%B").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder commitMsg = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    commitMsg.append(line).append("\n");
                }
                process.waitFor();
                String message = commitMsg.toString().trim();
                if (!message.isEmpty() && !isValidCommitMessage(message)) {
                    fail("El ultimo commit local ('" + message + "') no cumple la convencion Gitmoji + Conventional Commits. " +
                            "Ejemplo esperado: ':sparkles: feat(cobros): agregar calculo'");
                }
            }
        } catch (Exception e) {
            // Entornos sin binario de git ignoran la verificacion local
        }
    }
}
