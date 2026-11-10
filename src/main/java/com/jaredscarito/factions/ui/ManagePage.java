package com.jaredscarito.factions.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.jaredscarito.factions.service.AdminBoard;
import com.jaredscarito.factions.service.FactionService;
import com.jaredscarito.factions.text.Texts;

public final class ManagePage extends InteractiveCustomUIPage<ManagePage.ManageEvent> {
    private static final String PAGE = "Pages/ManagePage.ui";

    private final PlayerRef player;
    private final FactionService factions;
    private String selected;
    private String notice;
    private boolean confirmDisband;

    public ManagePage(PlayerRef player, FactionService factions) {
        super(player, CustomPageLifetime.CanDismiss, ManageEvent.CODEC);
        this.player = player;
        this.factions = factions;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(PAGE);
        AdminBoard.Snapshot board = factions.adminBoard(player, worldName(store), selected);
        AdminBoard.Detail detail = board.detail();
        selected = detail == null ? null : detail.id();
        if (board.factions().isEmpty()) {
            commands.appendInline("#Factions", rowButton(false));
            commands.set("#Factions[0].TextSpans", Texts.text("No factions yet", "#9eb0c3"));
        }
        for (int index = 0; index < board.factions().size(); index++) {
            AdminBoard.Row row = board.factions().get(index);
            boolean active = row.id().equals(selected);
            commands.appendInline("#Factions", rowButton(active));
            commands.set("#Factions[" + index + "].TextSpans", Message.join(
                    Message.raw(row.name()).color(row.color()).bold(true),
                    Message.raw("\n" + row.summary()).color(active ? "#ffffff" : "#9eb0c3")));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Factions[" + index + "]", EventData.of("Faction", row.id()));
        }
        if (detail == null) {
            commands.set("#Name.TextSpans", Texts.text("No factions", "#d7e4ea"));
            commands.set("#Stats.Visible", true);
            commands.set("#Stats.TextSpans", Texts.text("Create one with /f create, or mark a chunk as a safezone or warzone.", "#9eb0c3"));
            commands.set("#StatRow.Visible", false);
            commands.set("#Flags.Visible", false);
            commands.set("#About.Visible", false);
            commands.set("#Body.Visible", false);
            commands.set("#Claim.Visible", false);
        } else {
            commands.set("#Name.TextSpans", Texts.text(detail.name(), detail.color()));
            commands.set("#Stats.Visible", false);
            commands.set("#StatRow.Visible", true);
            commands.set("#MemberStat.TextSpans", Texts.text(detail.memberStat(), "#f3f7fb"));
            commands.set("#PowerStat.TextSpans", Texts.text(detail.powerStat(), "#e7c56a"));
            commands.set("#LandStat.TextSpans", Texts.text(detail.landStat(), detail.exposed() ? "#e07a7a" : "#f3f7fb"));
            commands.set("#BankStat.TextSpans", Texts.text(detail.bankStat(), "#9ddeb4"));
            commands.set("#BoostStat.TextSpans", Texts.text(detail.boostStat(), "0".equals(detail.boostStat()) ? "#9eb0c3" : "#e7c56a"));
            commands.set("#Flags.TextSpans", Texts.text(detail.flags(), "#c5d2df"));
            boolean showAbout = detail.about() != null && !detail.about().isBlank();
            commands.set("#About.Visible", showAbout);
            if (showAbout) {
                commands.set("#About.TextSpans", Texts.text(detail.about(), "#c5d0dc"));
            }
            commands.set("#Body.Visible", !detail.system());
            commands.set("#Claim.Visible", !detail.system());
            if (!detail.system()) {
                commands.set("#Lock.TextSpans", Texts.text(detail.locked() ? "Unlock" : "Lock", "#e8e6df"));
                commands.set("#Permanent.TextSpans", Texts.text(detail.permanent() ? "Not permanent" : "Permanent", "#e8e6df"));
                commands.set("#Open.TextSpans", Texts.text(detail.open() ? "Invite only" : "Open", "#e8e6df"));
                commands.set("#Peaceful.TextSpans", Texts.text(detail.peaceful() ? "Allow fights" : "Peaceful", "#e8e6df"));
                commands.set("#Disband.TextSpans", Texts.text(confirmDisband ? "Confirm" : "Disband", "#e8e6df"));
                commands.set("#DisbandHint.TextSpans", Texts.text(
                        confirmDisband ? "Click again to delete it and release the land." : "Deletes this faction and releases its land.",
                        "#8ea0b3"));
                bind(events, "#Lock", "lock");
                bind(events, "#Permanent", "permanent");
                bind(events, "#Open", "open");
                bind(events, "#Peaceful", "peaceful");
                bind(events, "#BoostUp", "boostup");
                bind(events, "#BoostDown", "boostdown");
                bind(events, "#BoostClear", "boostclear");
                bind(events, "#Claim", "claim");
                bind(events, "#Disband", "disband");
            }
            for (int index = 0; index < detail.members().size(); index++) {
                commands.appendInline("#Members", memberLine());
                commands.set("#Members[" + index + "].TextSpans", Texts.text(detail.members().get(index), "#d5deea"));
            }
        }
        boolean showNotice = notice != null && !notice.isBlank();
        commands.set("#Notice.Visible", showNotice);
        if (showNotice) {
            commands.set("#Notice.TextSpans", Texts.text(notice, "#e7c56a"));
        }
        commands.set("#Here.TextSpans", Texts.text(board.here(), "#d5deea"));
        commands.set("#Bypass.TextSpans", Texts.text(board.bypass() ? "Bypass on" : "Bypass off", board.bypass() ? "#e7c56a" : "#e8e6df"));
        bind(events, "#Unclaim", "unclaim");
        bind(events, "#Safezone", "safezone");
        bind(events, "#Warzone", "warzone");
        bind(events, "#Bypass", "bypass");
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ManageEvent data) {
        if (data == null || !player.hasPermission("factions.admin")) {
            return;
        }
        if (data.faction != null) {
            if (!data.faction.equals(selected)) {
                confirmDisband = false;
                notice = null;
            }
            selected = data.faction;
            rebuild();
            return;
        }
        if (data.action == null || data.action.isBlank()) {
            return;
        }
        if ("disband".equals(data.action) && !confirmDisband) {
            confirmDisband = true;
            notice = "Click Confirm disband to delete this faction and release its land.";
            rebuild();
            return;
        }
        if (!"disband".equals(data.action)) {
            confirmDisband = false;
        }
        World world = store.getExternalData().getWorld();
        notice = factions.adminManage(player, world, selected, data.action);
        confirmDisband = false;
        if ("disband".equals(data.action) && notice != null && notice.startsWith("Disbanded")) {
            selected = null;
        }
        rebuild();
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action));
    }

    private static String worldName(Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        return world == null ? null : world.getName();
    }

    private static String rowButton(boolean active) {
        String background = active ? "#2c4a62" : "#101820(0.45)";
        String hover = active ? "#3a6280" : "#1c2c3c";
        return "TextButton {"
                + " Padding: (Horizontal: 12, Vertical: 8);"
                + " Anchor: (Bottom: 4, Height: 52);"
                + " Style: ("
                + " Default: (LabelStyle: (FontSize: 14, Wrap: true, HorizontalAlignment: Start, VerticalAlignment: Center), Background: " + background + "),"
                + " Hovered: (LabelStyle: (FontSize: 14, Wrap: true, HorizontalAlignment: Start, VerticalAlignment: Center), Background: " + hover + ")"
                + " );"
                + "}";
    }

    private static String memberLine() {
        return "Label { Style: (FontSize: 14, Wrap: true, VerticalAlignment: Center, TextColor: #d5deea); Anchor: (Bottom: 8, Height: 42); }";
    }

    public static final class ManageEvent {
        public static final BuilderCodec<ManageEvent> CODEC = codec();
        public String faction;
        public String action;

        private static BuilderCodec<ManageEvent> codec() {
            BuilderCodec.Builder<ManageEvent> builder = BuilderCodec.builder(ManageEvent.class, ManageEvent::new);
            builder.append(new KeyedCodec<>("Faction", Codec.STRING), (event, value) -> event.faction = value, event -> event.faction).add();
            builder.append(new KeyedCodec<>("Action", Codec.STRING), (event, value) -> event.action = value, event -> event.action).add();
            return builder.build();
        }
    }
}
