package ai.corporatedroneagent.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

class WindowsDpapiSecretProtector implements SecretProtector {

    private static final String ALGORITHM = "windows-dpapi-current-user-v1";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private static final String PROTECT_SCRIPT = """
            Add-Type -AssemblyName System.Security
            $inputBase64 = [Console]::In.ReadToEnd().Trim()
            $bytes = [Convert]::FromBase64String($inputBase64)
            $protected = [System.Security.Cryptography.ProtectedData]::Protect(
                $bytes,
                $null,
                [System.Security.Cryptography.DataProtectionScope]::CurrentUser
            )
            [Console]::Out.Write([Convert]::ToBase64String($protected))
            """;

    private static final String UNPROTECT_SCRIPT = """
            Add-Type -AssemblyName System.Security
            $inputBase64 = [Console]::In.ReadToEnd().Trim()
            $protected = [Convert]::FromBase64String($inputBase64)
            $bytes = [System.Security.Cryptography.ProtectedData]::Unprotect(
                $protected,
                $null,
                [System.Security.Cryptography.DataProtectionScope]::CurrentUser
            )
            [Console]::Out.Write([Convert]::ToBase64String($bytes))
            """;

    @Override
    public StoredSecret protect(String secret) {
        String input = Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        return new StoredSecret(ALGORITHM, runPowerShell(PROTECT_SCRIPT, input));
    }

    @Override
    public String unprotect(StoredSecret secret) {
        ensureAlgorithm(secret);
        byte[] bytes = Base64.getDecoder().decode(runPowerShell(UNPROTECT_SCRIPT, secret.value()));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void ensureAlgorithm(StoredSecret secret) {
        if (!ALGORITHM.equals(secret.algorithm())) {
            throw new IllegalArgumentException("Unsupported secret algorithm: " + secret.algorithm());
        }
    }

    private String runPowerShell(String script, String input) {
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-EncodedCommand",
                    encodePowerShellCommand(script)
            ).start();
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Windows secret protection timed out.");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Windows secret protection failed: " + error);
            }

            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Windows secret protection failed to start.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Windows secret protection was interrupted.", exception);
        }
    }

    private String encodePowerShellCommand(String command) {
        byte[] bytes = command.getBytes(StandardCharsets.UTF_16LE);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
