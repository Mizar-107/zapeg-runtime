package io.github.mizar107.zapegruntime.journal;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Minimal item binding. No story node, clue, fact, or campaign state is stored here. */
public final class JournalTokenCodec {

    static final int SCHEMA = 1;
    private static final String ROOT = "HeraldorJournal";
    private static final String SCHEMA_KEY = "Schema";
    private static final String OWNER_KEY = "Owner";
    private static final String TOKEN_KEY = "Token";

    private JournalTokenCodec() {}

    public static void stamp(ItemStack stack, UUID ownerId, UUID token) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(token, "token");
        CompoundTag binding = new CompoundTag();
        binding.putInt(SCHEMA_KEY, SCHEMA);
        binding.putUUID(OWNER_KEY, ownerId);
        binding.putUUID(TOKEN_KEY, token);
        stack.getOrCreateTag().put(ROOT, binding);
    }

    public static Optional<Binding> read(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (!stack.is(JournalItems.HERALDOR_JOURNAL.get()) || stack.getCount() != 1) {
            return Optional.empty();
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag binding = root.getCompound(ROOT);
        if (binding.getAllKeys().size() != 3
                || !binding.contains(SCHEMA_KEY, Tag.TAG_INT)
                || binding.getInt(SCHEMA_KEY) != SCHEMA
                || !binding.hasUUID(OWNER_KEY)
                || !binding.hasUUID(TOKEN_KEY)) {
            return Optional.empty();
        }
        return Optional.of(new Binding(binding.getUUID(OWNER_KEY), binding.getUUID(TOKEN_KEY)));
    }

    public static boolean matches(ItemStack stack, UUID ownerId, UUID token) {
        return read(stack)
                .map(binding -> binding.ownerId().equals(ownerId)
                        && binding.token().equals(token))
                .orElse(false);
    }

    public static boolean hasBindingShape(ItemStack stack) {
        return read(stack).isPresent();
    }

    public record Binding(UUID ownerId, UUID token) {}
}
