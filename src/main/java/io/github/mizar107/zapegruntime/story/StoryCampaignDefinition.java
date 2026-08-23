package io.github.mizar107.zapegruntime.story;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Validated, fingerprinted 30-node campaign definition. */
public final class StoryCampaignDefinition {

    public static final int SCHEMA_VERSION = 1;
    public static final int REQUIRED_NODE_COUNT = 30;
    public static final int MAX_CAMPAIGN_REVISION = 1_000_000;
    private static final Pattern NODE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern JOURNAL_KEY =
            Pattern.compile("[a-z0-9][a-z0-9_.-]{0,127}");

    private final ResourceLocation id;
    private final int revision;
    private final String entryNodeId;
    private final List<StoryNode> nodes;
    private final Map<String, StoryNode> nodesById;
    private final String fingerprint;

    public StoryCampaignDefinition(
            ResourceLocation id,
            int revision,
            String entryNodeId,
            List<StoryNode> nodes) {
        this.id = Objects.requireNonNull(id, "id");
        this.revision = revision;
        this.entryNodeId = Objects.requireNonNull(entryNodeId, "entryNodeId");
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        validate();
        Map<String, StoryNode> indexed = new LinkedHashMap<>();
        for (StoryNode node : this.nodes) {
            indexed.put(node.id(), node);
        }
        this.nodesById = Collections.unmodifiableMap(indexed);
        this.fingerprint = fingerprint(this.id, this.revision, this.entryNodeId, this.nodes);
    }

    public ResourceLocation id() {
        return id;
    }

    public int revision() {
        return revision;
    }

    public String entryNodeId() {
        return entryNodeId;
    }

    public List<StoryNode> nodes() {
        return nodes;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public StoryNode node(String nodeId) {
        return nodesById.get(nodeId);
    }

    public int ordinalOf(String nodeId) {
        StoryNode node = node(nodeId);
        return node == null ? -1 : node.ordinal();
    }

    public List<String> completedPrefixFor(String currentNodeId) {
        int ordinal = ordinalOf(currentNodeId);
        if (ordinal < 0) {
            throw new IllegalArgumentException("unknown story node: " + currentNodeId);
        }
        List<String> completed = new ArrayList<>(ordinal);
        for (int index = 0; index < ordinal; index++) {
            completed.add(nodes.get(index).id());
        }
        return List.copyOf(completed);
    }

    private void validate() {
        if (revision < 1 || revision > MAX_CAMPAIGN_REVISION) {
            throw new IllegalArgumentException("campaign revision is outside the supported range");
        }
        validateNodeId(entryNodeId);
        if (nodes.size() != REQUIRED_NODE_COUNT) {
            throw new IllegalArgumentException(
                    "campaign must contain exactly " + REQUIRED_NODE_COUNT + " nodes");
        }
        if (!nodes.get(0).id().equals(entryNodeId)) {
            throw new IllegalArgumentException("entry node must be ordinal zero");
        }

        Set<String> ids = new HashSet<>();
        Set<String> journalKeys = new HashSet<>();
        Set<StoryTrigger> triggers = new HashSet<>();
        int previousChapter = 0;
        for (int index = 0; index < nodes.size(); index++) {
            StoryNode node = Objects.requireNonNull(nodes.get(index), "node " + index);
            validateNodeId(node.id());
            if (!ids.add(node.id())) {
                throw new IllegalArgumentException("duplicate story node id: " + node.id());
            }
            if (node.ordinal() != index) {
                throw new IllegalArgumentException("node ordinals must be contiguous from zero");
            }
            if (node.chapter() < 1 || node.chapter() > 9 || node.chapter() < previousChapter) {
                throw new IllegalArgumentException("node chapters must be monotonic and between 1 and 9");
            }
            previousChapter = node.chapter();
            if (!JOURNAL_KEY.matcher(node.journalKey()).matches()
                    || !journalKeys.add(node.journalKey())) {
                throw new IllegalArgumentException(
                        "journal keys must be valid and unique: " + node.journalKey());
            }

            boolean last = index == nodes.size() - 1;
            if (node.terminal() != last) {
                throw new IllegalArgumentException("only ordinal 29 may be terminal");
            }
            if (last) {
                if (node.advanceOn() != null || node.nextNodeId() != null) {
                    throw new IllegalArgumentException("terminal node cannot have a transition");
                }
            } else {
                if (node.advanceOn() == null || node.nextNodeId() == null) {
                    throw new IllegalArgumentException("non-terminal node requires one typed transition");
                }
                validateNodeId(node.nextNodeId());
                String expectedNext = nodes.get(index + 1).id();
                if (!expectedNext.equals(node.nextNodeId())) {
                    throw new IllegalArgumentException(
                            "campaign must advance to the immediately following ordinal");
                }
                if (!triggers.add(node.advanceOn())) {
                    throw new IllegalArgumentException(
                            "story transition predicates must be globally unique");
                }
            }
        }
    }

    static void validateNodeId(String nodeId) {
        if (nodeId == null || !NODE_ID.matcher(nodeId).matches()) {
            throw new IllegalArgumentException("invalid story node id: " + nodeId);
        }
    }

    private static String fingerprint(
            ResourceLocation id,
            int revision,
            String entryNodeId,
            List<StoryNode> nodes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, id.toString());
            append(digest, Integer.toString(revision));
            append(digest, entryNodeId);
            for (StoryNode node : nodes) {
                append(digest, node.id());
                append(digest, Integer.toString(node.ordinal()));
                append(digest, Integer.toString(node.chapter()));
                append(digest, node.journalKey());
                append(digest, Boolean.toString(node.terminal()));
                if (!node.terminal()) {
                    append(digest, node.advanceOn().type().serializedName());
                    append(digest, node.advanceOn().subject().toString());
                    append(digest, node.nextNodeId());
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void append(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String toHex(byte[] value) {
        StringBuilder encoded = new StringBuilder(value.length * 2);
        for (byte item : value) {
            encoded.append(Character.forDigit((item >>> 4) & 0xf, 16));
            encoded.append(Character.forDigit(item & 0xf, 16));
        }
        return encoded.toString();
    }
}
