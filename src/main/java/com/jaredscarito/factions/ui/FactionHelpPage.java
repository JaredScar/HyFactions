package com.jaredscarito.factions.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.OpenChatWithCommand;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.jaredscarito.factions.service.FactionService;
import com.jaredscarito.factions.text.Texts;

import java.util.List;

public final class FactionHelpPage extends InteractiveCustomUIPage<FactionHelpPage.HelpEvent> {
    private static final String PAGE = "Pages/FactionsPage.ui";

    private final PlayerRef player;
    private final FactionService factions;
    private final List<HelpCatalog.Category> categories;
    private String selected;

    public FactionHelpPage(PlayerRef player, FactionService factions) {
        super(player, CustomPageLifetime.CanDismiss, HelpEvent.CODEC);
        this.player = player;
        this.factions = factions;
        this.categories = HelpCatalog.categories(player.hasPermission("factions.admin"));
        this.selected = categories.get(0).id();
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(PAGE);
        HelpCatalog.Category current = categories.get(0);
        for (int index = 0; index < categories.size(); index++) {
            HelpCatalog.Category category = categories.get(index);
            boolean active = category.id().equals(selected);
            if (active) {
                current = category;
            }
            commands.appendInline("#Categories", categoryButton(active));
            commands.set("#Categories[" + index + "].TextSpans", Message.raw(category.name()).color(active ? "#ffffff" : "#d7e4ea").bold(true));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#Categories[" + index + "]", EventData.of("Category", category.id()));
        }
        commands.set("#CategoryName.TextSpans", Texts.text(current.name(), "#e7c56a"));
        commands.set("#Blurb.TextSpans", Texts.text(current.blurb(), "#d5deea"));
        List<HelpCatalog.Entry> entries = current.entries();
        for (int index = 0; index < entries.size(); index++) {
            HelpCatalog.Entry entry = entries.get(index);
            commands.appendInline("#Commands", commandCard());
            commands.set("#Commands[" + index + "].TextSpans", Message.join(
                    Message.raw(entry.usage()).color("#e7c56a").bold(true),
                    Message.raw("\n" + entry.detail()).color("#c5d0dc")));
            if ("manage".equals(entry.chat())) {
                events.addEventBinding(CustomUIEventBindingType.Activating, "#Commands[" + index + "]", EventData.of("Manage", "yes"));
            } else {
                events.addEventBinding(CustomUIEventBindingType.Activating, "#Commands[" + index + "]", EventData.of("Chat", entry.chat()));
            }
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, HelpEvent data) {
        if (data == null) {
            return;
        }
        if ("yes".equals(data.manage) && player.hasPermission("factions.admin")) {
            Player entity = store.getComponent(ref, Player.getComponentType());
            if (entity != null) {
                entity.getPageManager().openCustomPage(ref, store, new ManagePage(player, factions));
            }
            return;
        }
        if (data.chat != null && !data.chat.isBlank()) {
            Player entity = store.getComponent(ref, Player.getComponentType());
            if (entity != null) {
                entity.getPageManager().setPage(ref, store, Page.None);
            }
            player.getPacketHandler().write(new OpenChatWithCommand(data.chat));
            return;
        }
        if (data.category != null) {
            for (HelpCatalog.Category category : categories) {
                if (category.id().equals(data.category)) {
                    selected = category.id();
                    break;
                }
            }
        }
        rebuild();
    }

    private static String categoryButton(boolean active) {
        String background = active ? "#2c4a62" : "#101820(0.45)";
        String hover = active ? "#3a6280" : "#1c2c3c";
        return "TextButton {"
                + " Padding: (Horizontal: 12, Vertical: 8);"
                + " Anchor: (Bottom: 4, Height: 40);"
                + " Style: ("
                + " Default: (LabelStyle: (FontSize: 15, RenderBold: true, HorizontalAlignment: Start, VerticalAlignment: Center), Background: " + background + "),"
                + " Hovered: (LabelStyle: (FontSize: 15, RenderBold: true, HorizontalAlignment: Start, VerticalAlignment: Center), Background: " + hover + ")"
                + " );"
                + "}";
    }

    private static String commandCard() {
        return "TextButton {"
                + " Padding: (Horizontal: 12, Vertical: 8);"
                + " Anchor: (Bottom: 6, Height: 64);"
                + " Style: ("
                + " Default: (LabelStyle: (FontSize: 14, Wrap: true, HorizontalAlignment: Start, VerticalAlignment: Center), Background: #121a24),"
                + " Hovered: (LabelStyle: (FontSize: 14, Wrap: true, HorizontalAlignment: Start, VerticalAlignment: Center), Background: #243044)"
                + " );"
                + "}";
    }

    public static final class HelpEvent {
        public static final BuilderCodec<HelpEvent> CODEC = codec();
        public String category;
        public String chat;
        public String manage;

        private static BuilderCodec<HelpEvent> codec() {
            BuilderCodec.Builder<HelpEvent> builder = BuilderCodec.builder(HelpEvent.class, HelpEvent::new);
            builder.append(new KeyedCodec<>("Category", Codec.STRING), (event, value) -> event.category = value, event -> event.category).add();
            builder.append(new KeyedCodec<>("Chat", Codec.STRING), (event, value) -> event.chat = value, event -> event.chat).add();
            builder.append(new KeyedCodec<>("Manage", Codec.STRING), (event, value) -> event.manage = value, event -> event.manage).add();
            return builder.build();
        }
    }
}
