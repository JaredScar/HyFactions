package com.jaredscarito.factions;

import com.jaredscarito.factions.command.FactionChatCommand;
import com.jaredscarito.factions.command.FactionsCommand;
import com.jaredscarito.factions.service.FactionService;
import com.jaredscarito.factions.system.BreakGuard;
import com.jaredscarito.factions.system.CombatGuard;
import com.jaredscarito.factions.system.DamageGuard;
import com.jaredscarito.factions.system.DeathPower;
import com.jaredscarito.factions.system.PlaceGuard;
import com.jaredscarito.factions.system.TerritoryTicker;
import com.jaredscarito.factions.system.UseGuard;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class FactionsPlugin extends JavaPlugin {
    private final FactionService factions;

    public FactionsPlugin(JavaPluginInit init) {
        super(init);
        this.factions = new FactionService(this);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new FactionsCommand(factions));
        getCommandRegistry().registerCommand(new FactionChatCommand(factions));
        getEntityStoreRegistry().registerSystem(new BreakGuard(factions));
        getEntityStoreRegistry().registerSystem(new PlaceGuard(factions));
        getEntityStoreRegistry().registerSystem(new DamageGuard(factions));
        getEntityStoreRegistry().registerSystem(new UseGuard(factions));
        getEntityStoreRegistry().registerSystem(new CombatGuard(factions));
        getEntityStoreRegistry().registerSystem(new DeathPower(factions));
        getEntityStoreRegistry().registerSystem(new TerritoryTicker(factions));
        getEventRegistry().registerGlobal(PlayerChatEvent.class, factions::onChat);
        getEventRegistry().register(PlayerConnectEvent.class, event -> factions.remember(event.getPlayerRef()));
    }

    @Override
    protected void start() {
        factions.load();
    }

    @Override
    protected void shutdown() {
        factions.shutdown();
    }
}
