package io.github.mizar107.zapegruntime.journal.client;

import io.github.mizar107.zapegruntime.journal.JournalAction;
import io.github.mizar107.zapegruntime.journal.JournalView;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Responsive, narrated two-page presentation for the server-authorized journal prefix. */
public final class HeraldorJournalScreen extends Screen {

    private static final int INK = 0xff241710;
    private static final int MUTED_INK = 0xff6c5140;
    private static final int RED_INK = 0xff7b272b;
    private static final int PAPER = 0xffd7c29b;
    private static final int PAPER_DARK = 0xffb89b70;
    private static final int COVER = 0xff24161d;
    private static final int EDGE = 0xff6c3b43;

    private final JournalView view;
    private final List<Button> chapterButtons = new ArrayList<>();
    private int selectedOrdinal;
    private Button previousButton;
    private Button nextButton;
    private Button actionButton;
    private boolean actionPending;
    private Component transientStatus = Component.empty();

    public HeraldorJournalScreen(JournalView view) {
        super(Component.translatable("screen.zapeg_runtime.heraldor_journal.title"));
        this.view = Objects.requireNonNull(view, "view");
        this.selectedOrdinal = view.currentOrdinal();
    }

    @Override
    protected void init() {
        chapterButtons.clear();
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int tabGap = 2;
        int tabWidth = Math.max(28, (panelWidth - 16 - tabGap * 4) / 5);
        int tabStart = left + (panelWidth - (tabWidth * 5 + tabGap * 4)) / 2;
        for (int chapter = 1; chapter <= JournalView.CHAPTER_COUNT; chapter++) {
            final int targetChapter = chapter;
            Button tab = Button.builder(
                            Component.translatable(
                                    "screen.zapeg_runtime.heraldor_journal.chapter.short",
                                    chapter),
                            ignored -> select(view.latestInChapter(targetChapter)))
                    .bounds(tabStart + (chapter - 1) * (tabWidth + tabGap), top + 8, tabWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable(
                            "screen.zapeg_runtime.heraldor_journal.chapter." + chapter)))
                    .build();
            tab.active = view.chapterUnlocked(chapter);
            chapterButtons.add(addRenderableWidget(tab));
        }

        int bottom = top + panelHeight() - 30;
        previousButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.zapeg_runtime.heraldor_journal.previous"),
                        ignored -> select(selectedOrdinal - 1))
                .bounds(left + 14, bottom, 52, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "screen.zapeg_runtime.heraldor_journal.previous.tooltip")))
                .build());
        nextButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.zapeg_runtime.heraldor_journal.next"),
                        ignored -> select(selectedOrdinal + 1))
                .bounds(left + panelWidth - 66, bottom, 52, 20)
                .tooltip(Tooltip.create(Component.translatable(
                        "screen.zapeg_runtime.heraldor_journal.next.tooltip")))
                .build());

        JournalAction.forOrdinal(view.currentOrdinal()).ifPresent(action -> {
            String suffix = action == JournalAction.REVEAL_PALIMPSEST
                    ? "reveal_palimpsest"
                    : "count_absences";
            actionButton = addRenderableWidget(Button.builder(
                            Component.translatable(
                                    "screen.zapeg_runtime.heraldor_journal.action." + suffix),
                            ignored -> submit(action))
                    .bounds(left + panelWidth / 2 - 72, bottom, 144, 20)
                    .tooltip(Tooltip.create(Component.translatable(
                            "screen.zapeg_runtime.heraldor_journal.action.tooltip")))
                    .build());
        });
        updateControls();
    }

    private void submit(JournalAction action) {
        actionPending = true;
        if (actionButton != null) {
            actionButton.active = false;
        }
        transientStatus = Component.translatable(
                        "screen.zapeg_runtime.heraldor_journal.action.waiting")
                .withStyle(ChatFormatting.DARK_PURPLE);
        SceneNetwork.journalAction(action);
    }

    private void select(int ordinal) {
        if (!view.unlocked(ordinal)) {
            return;
        }
        selectedOrdinal = ordinal;
        transientStatus = Component.empty();
        updateControls();
        triggerImmediateNarration(true);
    }

    private void updateControls() {
        if (previousButton != null) {
            previousButton.active = selectedOrdinal > 0;
        }
        if (nextButton != null) {
            nextButton.active = selectedOrdinal < view.currentOrdinal();
        }
        if (actionButton != null) {
            actionButton.visible = selectedOrdinal == view.currentOrdinal();
            actionButton.active = actionButton.visible && !actionPending;
        }
        int selectedChapter = view.chapterFor(selectedOrdinal);
        for (int index = 0; index < chapterButtons.size(); index++) {
            Button button = chapterButtons.get(index);
            int chapter = index + 1;
            button.active = view.chapterUnlocked(chapter) && chapter != selectedChapter;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int center = left + panelWidth / 2;

        graphics.fill(left - 3, top - 3, left + panelWidth + 3, top + panelHeight + 3, COVER);
        graphics.renderOutline(left - 3, top - 3, panelWidth + 6, panelHeight + 6, EDGE);
        graphics.fill(left, top + 31, center - 2, top + panelHeight - 35, PAPER);
        graphics.fill(center + 2, top + 31, left + panelWidth, top + panelHeight - 35, PAPER_DARK);
        graphics.fill(center - 2, top + 31, center + 2, top + panelHeight - 35, 0xff735543);

        int pagePadding = 12;
        int pageWidth = panelWidth / 2 - pagePadding * 2 - 2;
        int contentTop = top + 41;
        Component chapter = Component.translatable(
                        "screen.zapeg_runtime.heraldor_journal.chapter." + view.chapterFor(selectedOrdinal))
                .withStyle(ChatFormatting.DARK_GRAY);
        graphics.drawString(font, chapter, left + pagePadding, contentTop, MUTED_INK, false);
        graphics.drawString(
                font,
                Component.translatable(JournalClientText.titleKey(selectedOrdinal))
                        .withStyle(ChatFormatting.BOLD),
                left + pagePadding,
                contentTop + 15,
                INK,
                false);
        renderWrapped(
                graphics,
                Component.translatable(JournalClientText.bodyKey(selectedOrdinal)),
                left + pagePadding,
                contentTop + 34,
                pageWidth,
                Math.max(3, (panelHeight - 118) / 10),
                INK);

        int right = center + pagePadding;
        graphics.drawString(
                font,
                Component.translatable(selectedOrdinal == view.currentOrdinal()
                                ? "screen.zapeg_runtime.heraldor_journal.current_clue"
                                : "screen.zapeg_runtime.heraldor_journal.resolved")
                        .withStyle(ChatFormatting.BOLD),
                right,
                contentTop,
                RED_INK,
                false);
        Component clue = selectedOrdinal == view.currentOrdinal()
                ? Component.translatable(JournalClientText.clueKey(selectedOrdinal))
                : Component.translatable("screen.zapeg_runtime.heraldor_journal.resolved.text");
        renderWrapped(
                graphics,
                clue,
                right,
                contentTop + 18,
                pageWidth,
                Math.max(3, (panelHeight - 134) / 10),
                selectedOrdinal == view.currentOrdinal() ? RED_INK : MUTED_INK);

        JournalAction.forOrdinal(selectedOrdinal).ifPresent(action -> {
            if (selectedOrdinal == view.currentOrdinal()) {
                renderDiscoveryMark(graphics, action, right, top, pageWidth, panelHeight);
            }
        });
        if (!transientStatus.getString().isEmpty()) {
            graphics.drawCenteredString(
                    font, transientStatus, center, top + panelHeight - 43, RED_INK);
        }
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "screen.zapeg_runtime.heraldor_journal.page",
                        selectedOrdinal + 1,
                        view.currentOrdinal() + 1),
                center,
                top + panelHeight - 20,
                0xffd7b9a0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderDiscoveryMark(
            GuiGraphics graphics,
            JournalAction action,
            int right,
            int top,
            int pageWidth,
            int panelHeight) {
        int y = top + panelHeight - 79;
        if (action == JournalAction.REVEAL_PALIMPSEST) {
            graphics.hLine(right, right + pageWidth, y, 0x667b272b);
            graphics.hLine(right + 9, right + pageWidth - 13, y + 4, 0x447b272b);
            graphics.hLine(right + 21, right + pageWidth - 4, y + 8, 0x337b272b);
        } else {
            for (int row = 0; row < 5; row++) {
                int inset = (row == 2 || row == 4) ? 22 : 5;
                graphics.hLine(right + inset, right + pageWidth - 5, y + row * 3, 0x557b272b);
            }
        }
    }

    private void renderWrapped(
            GuiGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int maxLines,
            int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            graphics.drawString(font, lines.get(index), x, y + index * 10, color, false);
        }
        if (lines.size() > count && count > 0) {
            graphics.drawString(font, Component.literal("…"), x + width - 8, y + (count - 1) * 10, color, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            select(selectedOrdinal - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            select(selectedOrdinal + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            select(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            select(view.currentOrdinal());
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            int chapter = keyCode - GLFW.GLFW_KEY_0;
            if (view.chapterUnlocked(chapter)) {
                select(view.latestInChapter(chapter));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public Component getNarrationMessage() {
        Component state = selectedOrdinal == view.currentOrdinal()
                ? Component.translatable(JournalClientText.clueKey(selectedOrdinal))
                : Component.translatable("screen.zapeg_runtime.heraldor_journal.resolved.text");
        return Component.translatable(JournalClientText.titleKey(selectedOrdinal))
                .append(". ")
                .append(Component.translatable(JournalClientText.bodyKey(selectedOrdinal)))
                .append(". ")
                .append(state);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelWidth() {
        return Math.max(160, Math.min(456, width - 16));
    }

    private int panelHeight() {
        return Math.max(150, Math.min(278, height - 16));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }
}
