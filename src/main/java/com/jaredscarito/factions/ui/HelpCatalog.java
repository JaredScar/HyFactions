package com.jaredscarito.factions.ui;

import java.util.ArrayList;
import java.util.List;

public final class HelpCatalog {
    private HelpCatalog() {
    }

    public record Entry(String usage, String detail, String chat) {
    }

    public record Category(String id, String name, String blurb, List<Entry> entries) {
    }

    public static List<Category> categories(boolean staff) {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("start", "Start",
                "Found a faction, or look one up.",
                List.of(
                        entry("/f create <name>", "Found a faction. You become the leader.", "/f create "),
                        entry("/f disband --confirm", "Delete your faction and release its land. Leader only.", "/f disband --confirm"),
                        entry("/f open", "Toggle open joining. Anyone can join an open faction.", "/f open"),
                        entry("/f info [name]", "Show a faction. Send it alone to see your own.", "/f info"),
                        entry("/f list", "List every faction.", "/f list"),
                        entry("/f desc <text>", "Set the description players see on /f info.", "/f desc "),
                        entry("/f peaceful", "Toggle peaceful. Peaceful land cannot be taken.", "/f peaceful"))));
        categories.add(new Category("people", "People",
                "Ranks run from recruit to member, officer, co-leader, and leader.",
                List.of(
                        entry("/f invite <player>", "Invite someone who is online.", "/f invite "),
                        entry("/f join [name]", "Accept an invite. Add the name if you have more than one.", "/f join"),
                        entry("/f deny", "Decline your invite.", "/f deny"),
                        entry("/f leave", "Leave your faction.", "/f leave"),
                        entry("/f kick <player>", "Remove a member.", "/f kick "),
                        entry("/f promote <player>", "Promote a member toward officer.", "/f promote "),
                        entry("/f demote <player>", "Demote an officer to member.", "/f demote "),
                        entry("/f leader <player>", "Hand leadership to another member.", "/f leader "),
                        entry("/f ban <player>", "Ban a player from the faction.", "/f ban "),
                        entry("/f unban <player>", "Remove a faction ban.", "/f unban "))));
        categories.add(new Category("land", "Land",
                "A claim is one 32 by 32 chunk, the full height of the world. Power limits how much land you can hold. The world map colors every claim.",
                List.of(
                        entry("/f claim", "Claim the chunk you are standing in.", "/f claim"),
                        entry("/f unclaim", "Release this chunk. Unclaiming refunds the claim cost.", "/f unclaim"),
                        entry("/f unclaimall --confirm", "Release every chunk your faction owns.", "/f unclaimall --confirm"),
                        entry("/f autoclaim", "Claim chunks as you walk.", "/f autoclaim"),
                        entry("/f claimline <count>", "Claim a line of chunks in the direction you face, up to 32.", "/f claimline "),
                        entry("/f claimsquare <radius>", "Claim a square around you. Radius up to 7.", "/f claimsquare "),
                        entry("/f claimcircle <radius>", "Claim a circle around you. Radius up to 7.", "/f claimcircle "),
                        entry("/f claimfill", "Fill the enclosed wilderness around you.", "/f claimfill"),
                        entry("/f map", "Show the chunks around you in chat. The world map shows the same claims.", "/f map"),
                        entry("/f owner <member>", "Set which member owns this chunk.", "/f owner "),
                        entry("/f access <player>", "Grant or remove build access on this chunk.", "/f access "),
                        entry("/f entry <allow|deny>", "Allow or deny enemies walking onto your land.", "/f entry "),
                        entry("/f tag <tag>", "Set the short tag shown in chat.", "/f tag "),
                        entry("/f title <text>", "Set the faction title.", "/f title "),
                        entry("/f announce <text>", "Set the message players see when they enter your land.", "/f announce "))));
        categories.add(new Category("travel", "Travel",
                "Home is one shared place. Warps are extra places, and a warp can ask for a password.",
                List.of(
                        entry("/f sethome", "Set the faction home where you stand.", "/f sethome"),
                        entry("/f home", "Teleport to the faction home.", "/f home"),
                        entry("/f setwarp <name> [password]", "Set a warp where you stand. Add a password to lock it.", "/f setwarp "),
                        entry("/f delwarp <name>", "Delete a warp.", "/f delwarp "),
                        entry("/f warp <name> [password]", "Teleport to a warp. Add the password if it has one.", "/f warp "),
                        entry("/f warps", "List the faction warps.", "/f warps"),
                        entry("/f stuck", "Escape another faction's land.", "/f stuck"))));
        categories.add(new Category("relations", "Relations",
                "In chat, your faction is green, allies are dark green, truces are yellow, and enemies are red. Enemies and neutrals can take land you do not have the power to hold.",
                List.of(
                        entry("/f ally <faction>", "Request or accept an alliance.", "/f ally "),
                        entry("/f truce <faction>", "Request or accept a truce.", "/f truce "),
                        entry("/f enemy <faction>", "Mark a faction as an enemy.", "/f enemy "),
                        entry("/f neutral <faction>", "Clear a relation.", "/f neutral "))));
        categories.add(new Category("chat", "Chat",
                "Faction chat stays with your faction. Start a public line with !.",
                List.of(
                        entry("/f chat [mode]", "Set chat to public, faction, ally, or truce. Send it alone to cycle.", "/f chat"),
                        entry("/f say <message>", "Send one message to your faction.", "/f say "),
                        entry("/fc <message>", "Send a message to your faction.", "/fc "))));
        categories.add(new Category("bank", "Bank",
                "Death costs power. A power boost stays until someone changes it.",
                List.of(
                        entry("/f power", "Show your power and how much land the faction holds.", "/f power"),
                        entry("/f money", "Show your balance and the faction bank.", "/f money"),
                        entry("/f deposit <amount>", "Move money from your balance into the faction bank.", "/f deposit "),
                        entry("/f withdraw <amount>", "Move money from the faction bank to your balance.", "/f withdraw "))));
        if (staff) {
            categories.add(new Category("staff", "Staff",
                    "Open management to work with a faction directly. The commands below still work for typed amounts.",
                    List.of(
                            entry("Open management", "List factions, then lock, disband, claim, or change a power boost.", "manage"),
                            entry("/f admin bypass", "Toggle building in other factions' claims.", "/f admin bypass"),
                            entry("/f admin unclaim", "Release the claim you are standing in.", "/f admin unclaim"),
                            entry("/f admin disband <name> --confirm", "Delete a faction and its claims.", "/f admin disband "),
                            entry("/f admin safezone", "Mark this chunk as a permanent safezone.", "/f admin safezone"),
                            entry("/f admin warzone", "Mark this chunk as a permanent warzone.", "/f admin warzone"),
                            entry("/f admin claim <faction>", "Claim this chunk for a faction.", "/f admin claim "),
                            entry("/f admin give <player> <amount>", "Add money to a player's balance.", "/f admin give "),
                            entry("/f admin power <player> <amount>", "Set a player's power.", "/f admin power "),
                            entry("/f admin powerboost <player> <amount>", "Set a player's power boost.", "/f admin powerboost "),
                            entry("/f admin factionboost <faction> <amount>", "Set a faction power boost.", "/f admin factionboost "),
                            entry("/f admin permanent <name>", "Toggle a permanent faction.", "/f admin permanent "),
                            entry("/f admin lock <name>", "Toggle a faction lock.", "/f admin lock "),
                            entry("/f admin reload", "Reload the faction config.", "/f admin reload"))));
        }
        return List.copyOf(categories);
    }

    private static Entry entry(String usage, String detail, String chat) {
        return new Entry(usage, detail, chat);
    }
}
