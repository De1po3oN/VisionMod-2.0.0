package com.visionmod;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** View nearby players and manage locally saved friends. */
public final class VisionFriendZoneScreen extends VisionPanelScreen {
    private static final int ROW_STEP = 25;
    private static final int ROW_HEIGHT = 22;
    private record Row(String name, int distance, boolean friend) {}

    private boolean savedTab;
    private int page;
    private int ticks;
    private EditBox nameInput;
    private List<VisionExtendedFeatures.NearbyPlayer> nearby = List.of();
    private List<Row> visibleRows = List.of();
    private int lastPage;
    private String feedback = "";

    public VisionFriendZoneScreen() { super("FRIEND ZONE"); }

    @Override protected void build() {
        String draft = nameInput == null ? "" : nameInput.getValue();
        nameInput = new EditBox(font, x+16, inputTop(), w-116, 20, Component.literal("Ник игрока"));
        nameInput.setMaxLength(VisionExtendedFeatures.FRIEND_NAME_LIMIT);
        nameInput.setHint(Component.literal("Ник игрока"));
        nameInput.setValue(draft);
        addRenderableWidget(nameInput);
        refreshNearby();
    }

    @Override public void tick() {
        super.tick();
        if (++ticks >= 10) refreshNearby();
    }

    private void refreshNearby() {
        ticks = 0;
        nearby = VisionExtendedFeatures.nearbyPlayers(minecraft);
    }

    private int listTop() { return layout.bodyTop()+32; }
    private int inputTop() { return layout.bodyBottom()-20; }
    private int rowsPerPage() { return Math.max(1, (inputTop()-14-listTop())/ROW_STEP); }
    private int tabWidth() { return (w-42)/2; }

    @Override protected void draw(GuiGraphics g, int mx, int my, float delta) {
        int top = layout.bodyTop(), tabWidth = tabWidth();
        button(g, x+16, top, tabWidth, 22, "Рядом · " + nearby.size(), !savedTab, mx, my);
        button(g, x+26+tabWidth, top, tabWidth, 22, "Друзья · " + VisionExtendedFeatures.friendCount(), savedTab, mx, my);
        List<Row> rows = savedTab
            ? VisionExtendedFeatures.friendList().stream().map(name -> new Row(name, -1, true)).toList()
            : nearby.stream().map(player -> new Row(player.name(), player.distance(), VisionExtendedFeatures.isFriend(player.name()))).toList();
        int perPage = rowsPerPage();
        lastPage = Math.max(0, (rows.size()-1)/perPage);
        page = Math.min(page, lastPage);
        int first = page*perPage;
        visibleRows = rows.subList(first, Math.min(first+perPage, rows.size()));
        for (int index = 0; index < visibleRows.size(); index++) drawRow(g, mx, my, index, visibleRows.get(index));
        if (visibleRows.isEmpty()) {
            String empty = savedTab ? "Список друзей пуст."
                : minecraft.level == null ? "Зайди в мир, чтобы увидеть игроков рядом."
                : "В радиусе " + VisionExtendedFeatures.FRIEND_RADIUS + " блоков никого нет.";
            VisionFonts.fit(g, font, empty, x+20, listTop()+12, w-40, VisionGuiTheme.MUTED);
        }
        if (!feedback.isEmpty()) {
            VisionFonts.fit(g, font, feedback, x+16, inputTop()-13, w-32, VisionGuiTheme.accent());
        }
        button(g, x+w-94, inputTop(), 78, 20, "Добавить", false, mx, my);
        if (lastPage > 0) {
            int bottom = y+h-29;
            button(g, x+16, bottom, 28, 20, "<", page > 0, mx, my);
            button(g, x+50, bottom, 28, 20, ">", page < lastPage, mx, my);
            VisionFonts.draw(g, font, (page+1) + " / " + (lastPage+1), x+88, bottom+6, VisionGuiTheme.MUTED);
        }
    }

    private void drawRow(GuiGraphics g, int mx, int my, int index, Row row) {
        int yy = listTop()+index*ROW_STEP;
        boolean hover = hit(mx, my, x+16, yy, w-32, ROW_HEIGHT);
        VisionGuiTheme.round(g, x+16, yy, w-32, ROW_HEIGHT, 4, hover ? VisionGuiTheme.HOVER : VisionGuiTheme.CARD);
        VisionFonts.fit(g, font, row.name(), x+25, yy+7, w-163, VisionGuiTheme.WHITE);
        if (row.distance() >= 0) {
            VisionFonts.centered(g, font, row.distance() + " м", x+w-114, yy+7, VisionGuiTheme.MUTED);
        }
        int actionX = x+w-88;
        VisionGuiTheme.round(g, actionX, yy+3, 68, 16, 4,
            row.friend() ? VisionGuiTheme.RAIL : VisionGuiTheme.tint(VisionGuiTheme.RAIL, .22f));
        VisionFonts.centered(g, font, row.friend() ? "Убрать" : "Добавить", actionX+34, yy+7,
            row.friend() ? VisionGuiTheme.MUTED : VisionGuiTheme.accent());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return super.mouseClicked(event, doubled);
        double mx = event.x(), my = event.y();
        int top = layout.bodyTop(), width = tabWidth();
        if (hit(mx, my, x+16, top, width, 22) || hit(mx, my, x+26+width, top, width, 22)) {
            savedTab = mx >= x+26+width;
            page = 0;
            visibleRows = List.of();
            feedback = "";
            return true;
        }
        if (hit(mx, my, x+w-94, inputTop(), 78, 20)) {
            if (add(nameInput.getValue())) nameInput.setValue("");
            return true;
        }
        int bottom = y+h-29;
        if (lastPage > 0 && hit(mx, my, x+16, bottom, 28, 20)) { page = Math.max(0, page-1); return true; }
        if (lastPage > 0 && hit(mx, my, x+50, bottom, 28, 20)) { page = Math.min(lastPage, page+1); return true; }
        for (int index = 0; index < visibleRows.size(); index++) {
            if (!hit(mx, my, x+16, listTop()+index*ROW_STEP, w-32, ROW_HEIGHT)) continue;
            String name = visibleRows.get(index).name();
            if (VisionExtendedFeatures.isFriend(name)) {
                VisionExtendedFeatures.removeFriend(name);
                feedback = "Убран из друзей: " + name;
            } else {
                add(name);
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (vertical != 0 && lastPage > 0 && hit(mx, my, x+16, listTop(), w-32, rowsPerPage()*ROW_STEP)) {
            page = Math.clamp(page+(vertical < 0 ? 1 : -1), 0, lastPage);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private boolean add(String value) {
        String name = value.strip();
        if (!VisionExtendedFeatures.validFriendName(name)) feedback = "Введи ник одного игрока.";
        else if (minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().name()))
            feedback = "Это твой ник.";
        else if (VisionExtendedFeatures.isFriend(name)) feedback = "Уже в друзьях: " + name;
        else if (VisionExtendedFeatures.addFriend(name)) {
            feedback = "Добавлен в друзья: " + name;
            return true;
        } else feedback = "Список друзей заполнен.";
        return false;
    }
}
