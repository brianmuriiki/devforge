package com.devforge.cli;

import com.devforge.core.AppContext;
import com.devforge.core.DevForgeException;
import java.util.Locale;
import java.util.Optional;

/** Guards destructive operations. Fails safe: no answer means no action. */
public final class Confirmation {

    private Confirmation() {}

    /**
     * Returns normally only if the user passed {@code --yes} or answered "y"/"yes".
     *
     * @throws DevForgeException if the user declined or no terminal is available
     */
    public static void require(AppContext context, ConfirmOptions options, String question) {
        if (options.yes) {
            return;
        }
        Optional<String> answer = context.prompter().ask(question + " [y/N] ");
        if (answer.isEmpty()) {
            throw new DevForgeException(
                    "Confirmation is required but no terminal is available. Nothing was changed. "
                            + "Re-run with --yes to confirm.");
        }
        String reply = answer.get().strip().toLowerCase(Locale.ROOT);
        if (!reply.equals("y") && !reply.equals("yes")) {
            throw new DevForgeException("Cancelled. Nothing was changed.");
        }
    }
}
