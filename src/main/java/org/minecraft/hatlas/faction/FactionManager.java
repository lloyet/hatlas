package org.minecraft.hatlas.faction;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.hatlas.crystal.HatlasCrystal;
import org.minecraft.hatlas.crystal.HatlasCrystalManager;
import org.minecraft.hatlas.job.JobManager;
import org.minecraft.hatlas.listener.LocatorBarListener;
import org.minecraft.hatlas.storage.DataDomain;
import org.minecraft.hatlas.storage.ItemBytes;
import org.minecraft.hatlas.storage.Storage;
import org.minecraft.hatlas.storage.model.FactionsData;
import org.minecraft.hatlas.teleport.HomeManager;

import java.util.*;

public class FactionManager {

    // factionName -> Faction
    private static final Map<String, Faction> factions = new HashMap<>();
    // playerUUID -> factionName (quick reverse lookup)
    private static final Map<UUID, String> playerFaction = new HashMap<>();
    // invitedUUID -> factionName (pending invitations)
    private static final Map<UUID, String> pendingInvitations = new HashMap<>();
    // targetFactionName -> set of requesting faction names (in-memory, not persisted)
    private static final Map<String, Set<String>> pendingAllyRequests = new HashMap<>();

    /** Creates a new faction. Returns false if the name is taken or the owner is already in a faction. */
    public static boolean createFaction(String factionName, UUID ownerUUID) {
        if (factions.containsKey(factionName)) return false;
        if (playerFaction.containsKey(ownerUUID)) return false;
        factions.put(factionName, new Faction(factionName, ownerUUID));
        playerFaction.put(ownerUUID, factionName);
        persist();
        return true;
    }

    /**
     * Saves factions AND crystals right away, in one transaction. Both must be written
     * together: a crystal saved without its faction (or a disband not saved while its
     * crystal is) leaves an orphan crystal that keeps its claims after a crash (known issue §7.10).
     */
    public static void persist() {
        Storage.save(DataDomain.FACTIONS, DataDomain.CRYSTALS);
    }

    /** Deletes a faction. Only the owner can do this. */
    public static boolean deleteFaction(UUID requesterUUID) {
        String factionName = playerFaction.get(requesterUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        if (!faction.getOwner().equals(requesterUUID)) return false;
        List<UUID> formerMembers = getFactionPlayers(factionName);
        HatlasCrystalManager.removeAllForFaction(factionName);
        FactionClaimManager.removeAllClaims(factionName);
        faction.getMembers().forEach(uuid -> {
            playerFaction.remove(uuid);
            JobManager.removeJob(uuid);
            forfeitSecondaryHomes(uuid);
        });
        JobManager.removeJob(requesterUUID);
        playerFaction.remove(requesterUUID);
        forfeitSecondaryHomes(requesterUUID);
        factions.remove(factionName);
        persist();
        LocatorBarListener.refresh(formerMembers);
        return true;
    }

    /** Renames a faction. Owner or Leaders can do this. Returns false if the new name is already taken. */
    public static boolean renameFaction(String newName, UUID requesterUUID) {
        String oldName = playerFaction.get(requesterUUID);
        if (oldName == null) return false;
        Faction faction = factions.get(oldName);
        boolean canRename = faction.getOwner().equals(requesterUUID)
                || faction.getRole(requesterUUID).isAtLeast(FactionRole.LEADER);
        if (!canRename) return false;
        if (factions.containsKey(newName)) return false;
        faction.setName(newName);
        factions.remove(oldName);
        factions.put(newName, faction);
        playerFaction.put(faction.getOwner(), newName);
        faction.getMembers().forEach(uuid -> playerFaction.put(uuid, newName));
        pendingInvitations.replaceAll((uuid, name) -> name.equals(oldName) ? newName : name);
        HatlasCrystalManager.renameFactionCrystals(oldName, newName);
        FactionClaimManager.renameFactionClaims(oldName, newName);
        return true;
    }

    /** Sends an invitation. Owner, Leaders, and Moderators can invite. Returns false if target is already in a faction. */
    public static boolean invitePlayer(UUID inviterUUID, UUID targetUUID) {
        String factionName = playerFaction.get(inviterUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        boolean canInvite = faction.getOwner().equals(inviterUUID)
                || faction.getRole(inviterUUID).isAtLeast(FactionRole.MODERATOR);
        if (!canInvite) return false;
        if (playerFaction.containsKey(targetUUID)) return false;
        pendingInvitations.put(targetUUID, factionName);
        return true;
    }

    /** Accepts a pending invitation. Returns the faction name joined, or null if no invitation exists. */
    public static String acceptInvitation(UUID playerUUID) {
        String factionName = pendingInvitations.remove(playerUUID);
        if (factionName == null) return null;
        Faction faction = factions.get(factionName);
        if (faction == null) return null;
        faction.addMember(playerUUID);
        playerFaction.put(playerUUID, factionName);
        LocatorBarListener.refresh(getFactionPlayers(factionName));
        return factionName;
    }

    /** Force-adds a player to a faction, bypassing invitations. Returns false if the player is already in a faction or the faction does not exist. */
    public static boolean forceJoin(UUID playerUUID, String factionName) {
        if (playerFaction.containsKey(playerUUID)) return false;
        Faction faction = factions.get(factionName);
        if (faction == null) return false;
        faction.addMember(playerUUID);
        playerFaction.put(playerUUID, factionName);
        LocatorBarListener.refresh(getFactionPlayers(factionName));
        return true;
    }

    /** Declines a pending invitation. Returns false if no invitation exists. */
    public static boolean declineInvitation(UUID playerUUID) {
        return pendingInvitations.remove(playerUUID) != null;
    }

    /** Lets a non-owner member leave their faction voluntarily. Returns false if not in a faction or is the owner. */
    public static boolean leaveFaction(UUID playerUUID) {
        String factionName = playerFaction.get(playerUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        if (faction.getOwner().equals(playerUUID)) return false;
        List<UUID> affected = getFactionPlayers(factionName);
        faction.removeMember(playerUUID);
        faction.removeRole(playerUUID);
        playerFaction.remove(playerUUID);
        JobManager.removeJob(playerUUID);
        forfeitSecondaryHomes(playerUUID);
        LocatorBarListener.refresh(affected);
        return true;
    }

    /** Kicks a member from the faction. Owner or Moderators+ can do this. */
    public static boolean kickPlayer(UUID requesterUUID, UUID targetUUID) {
        String factionName = playerFaction.get(requesterUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        boolean requesterIsOwner = faction.getOwner().equals(requesterUUID);
        FactionRole requesterRole = requesterIsOwner ? null : faction.getRole(requesterUUID);
        if (!requesterIsOwner && !requesterRole.isAtLeast(FactionRole.MODERATOR)) return false;
        if (!faction.getMembers().contains(targetUUID)) return false;
        FactionRole targetRole = faction.getRole(targetUUID);
        if (!requesterIsOwner) {
            if (requesterRole == FactionRole.MODERATOR && targetRole.isAtLeast(FactionRole.MODERATOR)) return false;
            if (requesterRole == FactionRole.LEADER && targetRole == FactionRole.LEADER) return false;
        }
        List<UUID> affected = getFactionPlayers(factionName);
        faction.removeMember(targetUUID);
        faction.removeRole(targetUUID);
        playerFaction.remove(targetUUID);
        JobManager.removeJob(targetUUID);
        forfeitSecondaryHomes(targetUUID);
        LocatorBarListener.refresh(affected);
        return true;
    }

    /** Sets the faction description. Owner or Leaders can do this. */
    public static boolean setFactionDescription(UUID requesterUUID, String description) {
        String factionName = playerFaction.get(requesterUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        boolean canEdit = faction.getOwner().equals(requesterUUID)
                || faction.getRole(requesterUUID).isAtLeast(FactionRole.LEADER);
        if (!canEdit) return false;
        faction.setDescription(description);
        return true;
    }

    /** Promotes a member to the next role (MEMBER → BANKER → MODERATOR → LEADER). Only the owner can promote. */
    public static boolean promotePlayer(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!factionName.equals(playerFaction.get(targetUUID))) return false;
        if (faction.getOwner().equals(targetUUID)) return false;
        FactionRole current = faction.getRole(targetUUID);
        if (current == FactionRole.LEADER) return false;
        FactionRole[] values = FactionRole.values();
        faction.setRole(targetUUID, values[current.ordinal() + 1]);
        return true;
    }

    /** Transfers ownership to a current member. The old owner becomes a Leader. */
    public static boolean transferOwnership(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!faction.getMembers().contains(targetUUID)) return false;
        faction.removeMember(targetUUID);
        faction.removeRole(targetUUID);
        faction.addMember(ownerUUID);
        faction.setRole(ownerUUID, FactionRole.LEADER);
        faction.setOwner(targetUUID);
        return true;
    }

    /** Demotes a member to the previous role (LEADER → MODERATOR → BANKER → MEMBER). Only the owner can demote. */
    public static boolean demotePlayer(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!factionName.equals(playerFaction.get(targetUUID))) return false;
        if (faction.getOwner().equals(targetUUID)) return false;
        FactionRole current = faction.getRole(targetUUID);
        if (current == FactionRole.MEMBER) return false;
        FactionRole[] values = FactionRole.values();
        faction.setRole(targetUUID, values[current.ordinal() - 1]);
        return true;
    }

    /**
     * True when the player may spend the faction vault's rubies (take gems out of the vault,
     * buy in a job shop or on the auction house): the owner, or a BANKER or higher.
     */
    public static boolean canSpendVault(Faction faction, UUID uuid) {
        return faction != null && (faction.getOwner().equals(uuid)
                || faction.getRole(uuid).isAtLeast(FactionRole.BANKER));
    }

    /** Returns the role of a player in their faction, or null if they are the owner or not in any faction. */
    public static FactionRole getPlayerRole(UUID playerUUID) {
        String factionName = playerFaction.get(playerUUID);
        if (factionName == null) return null;
        Faction faction = factions.get(factionName);
        if (faction.getOwner().equals(playerUUID)) return null;
        return faction.getRole(playerUUID);
    }

    /** Returns the faction name of a player, or null if not in any faction. */
    public static String getPlayerFaction(UUID playerUUID) {
        return playerFaction.get(playerUUID);
    }

    /** Returns all members of a faction (owner + members), or an empty list if the faction doesn't exist. */
    public static List<UUID> getFactionPlayers(String factionName) {
        Faction faction = factions.get(factionName);
        if (faction == null) return List.of();
        List<UUID> all = new ArrayList<>();
        all.add(faction.getOwner());
        all.addAll(faction.getMembers());
        return all;
    }

    public static boolean hasPendingInvitation(UUID playerUUID) {
        return pendingInvitations.containsKey(playerUUID);
    }

    public static String getPendingInvitationFaction(UUID playerUUID) {
        return pendingInvitations.get(playerUUID);
    }

    public static Map<String, Faction> getFactions() { return factions; }

    public static Faction getFaction(String factionName) { return factions.get(factionName); }

    /** Sends a chat message to every online member of a faction, optionally excluding one player. */
    public static void broadcastToFaction(String factionName, net.kyori.adventure.text.Component message, UUID exclude) {
        broadcastToFaction(factionName, message, exclude, null);
    }

    /** Same as above, skipping two players (e.g. the promoter and the promoted, who get their own message). */
    public static void broadcastToFaction(String factionName, net.kyori.adventure.text.Component message,
                                          UUID exclude, UUID exclude2) {
        Faction faction = factions.get(factionName);
        if (faction == null) return;
        java.util.Set<UUID> skip = new java.util.HashSet<>();
        skip.add(exclude);
        skip.add(exclude2);
        if (!skip.contains(faction.getOwner())) {
            Player owner = Bukkit.getPlayer(faction.getOwner());
            if (owner != null) owner.sendMessage(message);
        }
        for (UUID memberUUID : faction.getMembers()) {
            if (skip.contains(memberUUID)) continue;
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) member.sendMessage(message);
        }
    }

    /**
     * Returns a list of all online members of a faction, optionally excluding one player.
     */
    public static List<Player> getOnlineFactionMembers(String factionName, UUID exclude) {
        Faction faction = factions.get(factionName);
        if (faction == null) return List.of();
        List<Player> online = new ArrayList<>();
        if (!faction.getOwner().equals(exclude)) {
            Player owner = Bukkit.getPlayer(faction.getOwner());
            if (owner != null) online.add(owner);
        }
        for (UUID memberUUID : faction.getMembers()) {
            if (memberUUID.equals(exclude)) continue;
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) online.add(member);
        }
        return online;
    }

    /** Disbands a faction by name without requiring the owner UUID. Used when all crystals are destroyed. */
    public static void disbandFaction(String factionName) {
        Faction faction = factions.get(factionName);
        if (faction == null) return;
        List<UUID> formerMembers = getFactionPlayers(factionName);
        HatlasCrystalManager.removeAllForFaction(factionName);
        FactionClaimManager.removeAllClaims(factionName);
        faction.getMembers().forEach(uuid -> {
            playerFaction.remove(uuid);
            JobManager.removeJob(uuid);
            forfeitSecondaryHomes(uuid);
        });
        JobManager.removeJob(faction.getOwner());
        playerFaction.remove(faction.getOwner());
        forfeitSecondaryHomes(faction.getOwner());
        factions.remove(factionName);
        persist();
        LocatorBarListener.refresh(formerMembers);
    }

    /**
     * Drops every home except the reserved main and persists the change so the player
     * can't reclaim the bonus slots on relog. Called after the player loses faction
     * membership for any reason.
     */
    private static void forfeitSecondaryHomes(UUID playerUUID) {
        HomeManager.removeSecondaryHomes(playerUUID);
        Storage.save(DataDomain.HOMES);
    }

    /** Changes the color of the faction. Owner or Leaders can do this. */
    public static boolean setFactionColor(UUID requesterUUID, NamedTextColor color) {
        String factionName = playerFaction.get(requesterUUID);
        if (factionName == null) return false;
        Faction faction = factions.get(factionName);
        boolean canChange = faction.getOwner().equals(requesterUUID)
                || faction.getRole(requesterUUID).isAtLeast(FactionRole.LEADER);
        if (!canChange) return false;
        faction.setColor(color);
        for (HatlasCrystal crystal : HatlasCrystalManager.getFactionCrystals(factionName)) {
            crystal.updateNametag();
        }
        return true;
    }

    // ── Level / EXP / Skill-point system ─────────────────────────────────────

    /**
     * Adds exp to a faction. Levels up freely (no checkpoint lock) and awards skill points.
     * Returns an empty list (kept for API compatibility).
     */
    public static List<Integer> addExpToFaction(String factionName, int amount) {
        Faction faction = factions.get(factionName);
        if (faction == null || faction.getLevel() >= FactionLevelManager.MAX_LEVEL) return List.of();

        faction.addExp(amount);

        while (faction.getLevel() < FactionLevelManager.MAX_LEVEL) {
            int required = FactionLevelManager.getExpRequiredForLevel(faction.getLevel() + 1);
            if (faction.getExp() >= required) {
                int overflow = faction.getExp() - required;
                faction.setLevel(faction.getLevel() + 1);
                faction.setExp(overflow);
                faction.addSkillPoints(FactionLevelManager.getSkillPointsPerLevel());
            } else {
                break;
            }
        }

        for (HatlasCrystal crystal : HatlasCrystalManager.getFactionCrystals(factionName)) {
            crystal.updateNametag();
        }

        return List.of();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    /** Immutable copy of every faction, for storage. Main thread only. */
    public static FactionsData snapshot() {
        // An open vault GUI holds the live contents: copy them in before serializing.
        org.minecraft.hatlas.gui.CrystalVaultGui.pullAllLive();
        List<FactionsData.FactionRow> rows = new ArrayList<>();
        int defaultVault = FactionLevelManager.getDefaultVaultSize();
        for (Faction f : factions.values()) {
            List<FactionsData.Member> members = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (UUID m : f.getMembers()) {
                if (!seen.add(m)) continue;
                FactionRole role = f.getRoles().get(m);
                members.add(new FactionsData.Member(m, true, role == null ? null : role.name()));
            }
            f.getRoles().forEach((uuid, role) -> {
                if (seen.add(uuid)) members.add(new FactionsData.Member(uuid, false, role.name()));
            });
            String colorName = NamedTextColor.NAMES.key(f.getColor());
            rows.add(new FactionsData.FactionRow(f.getName(), f.getOwner(),
                    colorName != null ? colorName : "white", f.getDescription() == null ? "" : f.getDescription(),
                    f.getLevel(), f.getExp(), f.getSkillPoints(), f.isOutpostUnlocked(),
                    f.getVaultSize() != defaultVault ? f.getVaultSize() : null,
                    f.getDowngradeMul(), f.getDowngradeWindowEndMs(),
                    List.copyOf(members), List.copyOf(f.getAllies()),
                    List.copyOf(new TreeSet<>(f.getPurchasedHomeTiers())),
                    List.copyOf(new TreeSet<>(f.getPurchasedVaultTiers())),
                    ItemBytes.fromArray(f.getVaultContents())));
        }
        return new FactionsData(List.copyOf(rows));
    }

    /** Rebuilds factions and the player → faction index from storage. Main thread only. */
    public static void restore(FactionsData data) {
        factions.clear();
        playerFaction.clear();
        int defaultVault = FactionLevelManager.getDefaultVaultSize();
        for (FactionsData.FactionRow row : data.factions()) {
            Faction faction = new Faction(row.name(), row.owner());
            playerFaction.put(row.owner(), row.name());

            NamedTextColor color = NamedTextColor.NAMES.value(row.color());
            faction.setColor(color != null ? color : NamedTextColor.WHITE);
            faction.setDescription(row.description());
            faction.setLevel(row.level());
            faction.setExp(row.exp());
            faction.setSkillPoints(row.skillPoints());

            for (FactionsData.Member m : row.members()) {
                if (m.member()) {
                    faction.addMember(m.uuid());
                    playerFaction.put(m.uuid(), row.name());
                }
                if (m.role() != null) {
                    try {
                        faction.setRole(m.uuid(), FactionRole.valueOf(m.role()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            for (String ally : row.allies()) faction.addAlly(ally);

            faction.setDowngradeMul(row.downgradeMul());
            faction.setDowngradeWindowEndMs(row.downgradeWindowEnd());
            faction.setOutpostUnlocked(row.outpostUnlocked());
            for (int idx : row.homeTiers()) faction.addPurchasedHomeTier(idx);
            for (int idx : row.vaultTiers()) faction.addPurchasedVaultTier(idx);

            if (row.vaultSize() != null && row.vaultSize() != defaultVault) faction.setVaultSize(row.vaultSize());
            ItemStack[] saved = ItemBytes.toArray(row.vaultContents());
            ItemStack[] vault = faction.getVaultContents();
            for (int i = 0; i < saved.length && i < vault.length; i++) vault[i] = saved[i];

            factions.put(row.name(), faction);
        }
    }

    // ── Alliance request helpers ──────────────────────────────────────────────

    /** Returns true if {@code fromFaction} already sent a pending ally request to {@code toFaction}. */
    public static boolean hasPendingAllyRequest(String fromFaction, String toFaction) {
        Set<String> requests = pendingAllyRequests.get(toFaction);
        return requests != null && requests.contains(fromFaction);
    }

    /** Registers a pending ally request from {@code fromFaction} to {@code toFaction}. */
    public static void sendAllyRequest(String fromFaction, String toFaction) {
        pendingAllyRequests.computeIfAbsent(toFaction, k -> new HashSet<>()).add(fromFaction);
    }

    /**
     * Accepts the ally request: links both factions, removes the pending request.
     * Returns false if the request doesn't exist or either faction is missing.
     */
    public static boolean acceptAllyRequest(String acceptingFaction, String requestingFaction) {
        Set<String> requests = pendingAllyRequests.get(acceptingFaction);
        if (requests == null || !requests.remove(requestingFaction)) return false;
        if (requests.isEmpty()) pendingAllyRequests.remove(acceptingFaction);
        Faction f1 = factions.get(acceptingFaction);
        Faction f2 = factions.get(requestingFaction);
        if (f1 == null || f2 == null) return false;
        f1.addAlly(requestingFaction);
        f2.addAlly(acceptingFaction);
        return true;
    }

    /**
     * Denies the ally request and removes it.
     * Returns false if the request doesn't exist.
     */
    public static boolean denyAllyRequest(String denyingFaction, String requestingFaction) {
        Set<String> requests = pendingAllyRequests.get(denyingFaction);
        if (requests == null || !requests.remove(requestingFaction)) return false;
        if (requests.isEmpty()) pendingAllyRequests.remove(denyingFaction);
        return true;
    }

    // ── Alliance helpers ──────────────────────────────────────────────────────

    public static boolean areAllied(String faction1, String faction2) {
        if (faction1 == null || faction2 == null) return false;
        if (faction1.equals(faction2)) return true;
        Faction f1 = factions.get(faction1);
        Faction f2 = factions.get(faction2);
        return f1 != null && f2 != null && f1.hasAlly(faction2) && f2.hasAlly(faction1);
    }
}
