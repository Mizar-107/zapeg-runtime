package io.github.mizar107.zapegruntime.journal.client;

import io.github.mizar107.zapegruntime.journal.JournalView;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Client-only entry point kept behind the packet's DistExecutor guard. */
public final class JournalClient {

    private JournalClient() {}

    public static void open(JournalView view) {
        Minecraft.getInstance().setScreen(
                new HeraldorJournalScreen(Objects.requireNonNull(view, "view")));
    }
}
