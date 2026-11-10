package com.jaredscarito.factions.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.jaredscarito.factions.service.FactionService;

public final class FactionsCommand extends AbstractPlayerCommand {
    private final FactionService factions;

    public FactionsCommand(FactionService factions) {
        super("f", "Create and manage a faction");
        requireNoPermission();
        addAliases("faction", "factions");
        this.factions = factions;
        addSubCommand(new HelpCommand(factions));
        addSubCommand(new CreateCommand(factions));
        addSubCommand(new DisbandCommand(factions));
        addSubCommand(new InviteCommand(factions));
        addSubCommand(new JoinCommand(factions));
        addSubCommand(new DenyCommand(factions));
        addSubCommand(new LeaveCommand(factions));
        addSubCommand(new KickCommand(factions));
        addSubCommand(new PromoteCommand(factions));
        addSubCommand(new DemoteCommand(factions));
        addSubCommand(new LeaderCommand(factions));
        addSubCommand(new ClaimCommand(factions));
        addSubCommand(new UnclaimCommand(factions));
        addSubCommand(new UnclaimAllCommand(factions));
        addSubCommand(new AutoClaimCommand(factions));
        addSubCommand(new ClaimLineCommand(factions));
        addSubCommand(new ClaimSquareCommand(factions));
        addSubCommand(new ClaimCircleCommand(factions));
        addSubCommand(new ClaimFillCommand(factions));
        addSubCommand(new MapCommand(factions));
        addSubCommand(new SetHomeCommand(factions));
        addSubCommand(new HomeCommand(factions));
        addSubCommand(new ChatCommand(factions));
        addSubCommand(new SayCommand(factions));
        addSubCommand(new PowerCommand(factions));
        addSubCommand(new MoneyCommand(factions));
        addSubCommand(new DepositCommand(factions));
        addSubCommand(new WithdrawCommand(factions));
        addSubCommand(new InfoCommand(factions));
        addSubCommand(new ListCommand(factions));
        addSubCommand(new DescCommand(factions));
        addSubCommand(new AllyCommand(factions));
        addSubCommand(new TruceCommand(factions));
        addSubCommand(new EnemyCommand(factions));
        addSubCommand(new NeutralCommand(factions));
        addSubCommand(new OwnerCommand(factions));
        addSubCommand(new AccessCommand(factions));
        addSubCommand(new EntryCommand(factions));
        addSubCommand(new OpenCommand(factions));
        addSubCommand(new BanCommand(factions));
        addSubCommand(new UnbanCommand(factions));
        addSubCommand(new TagCommand(factions));
        addSubCommand(new TitleCommand(factions));
        addSubCommand(new AnnounceCommand(factions));
        addSubCommand(new PeacefulCommand(factions));
        addSubCommand(new SetWarpCommand(factions));
        addSubCommand(new DeleteWarpCommand(factions));
        addSubCommand(new WarpCommand(factions));
        addSubCommand(new WarpsCommand(factions));
        addSubCommand(new StuckCommand(factions));
        addSubCommand(new AdminCommand(factions));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        HelpPages.open(factions, store, ref, player);
    }
}
