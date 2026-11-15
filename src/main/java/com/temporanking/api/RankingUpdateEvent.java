package com.temporanking.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Disparado (na thread principal) sempre que um ranking e recalculado.
 * Bom para atualizar coisas do Skript (variaveis, scoreboards) junto com o ranking.
 *
 * <pre>
 * on RankingUpdateEvent:
 *     if event.getType() is "ricos":
 *         set {top1rico} to TempoRankingAPI.getTopMoneyName(1)
 * </pre>
 */
public final class RankingUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String type;
    private final int size;

    public RankingUpdateEvent(String type, int size) {
        this.type = type;
        this.size = size;
    }

    /** "tempo" ou "ricos" */
    public String getType() {
        return type;
    }

    /** Quantos jogadores entraram no ranking (sem os ignorados). */
    public int getSize() {
        return size;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
