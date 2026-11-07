package org.minecraft.hatlas.storage.model;

/**
 * One row of the ruby ledger ({@code ruby_ledger}): an append-only record of rubies moving
 * between parties. A null sender means the rubies were created (mined, given by an admin);
 * a null receiver means they left the economy (spent in a shop, lost). Party types are
 * {@code PLAYER} (id = UUID) or {@code FACTION} (id = faction name). Amounts are in gems
 * (a ruby block counts 9). Location fields are null when the move has no place.
 */
public record LedgerRow(long at, String kind, int amount,
                        String senderType, String senderId, String senderName, String senderFaction,
                        String receiverType, String receiverId, String receiverName, String receiverFaction,
                        String actorUuid, String actorName, String ref,
                        String world, Integer x, Integer y, Integer z) {}
