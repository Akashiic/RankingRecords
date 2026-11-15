package com.temporanking.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Disparado (na thread principal) quando uma posicao do top muda de dono.
 * Ex.: alguem virou o novo TOP 1 de tempo.
 *
 * <pre>
 * import:
 *     com.temporanking.api.TopPositionChangeEvent
 *
 * on TopPositionChangeEvent:
 *     if event.getPosition() is 1:
 *         broadcast "%event.getNewName()% agora e TOP 1 em %event.getType()%!"
 * </pre>
 */
public final class TopPositionChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String type;
    private final int position;
    private final UUID oldUuid;
    private final String oldName;
    private final UUID newUuid;
    private final String newName;

    public TopPositionChangeEvent(String type, int position, UUID oldUuid, String oldName, UUID newUuid, String newName) {
        this.type = type;
        this.position = position;
        this.oldUuid = oldUuid;
        this.oldName = oldName;
        this.newUuid = newUuid;
        this.newName = newName;
    }

    /** "tempo" ou "ricos" */
    public String getType() {
        return type;
    }

    public boolean isTempo() {
        return "tempo".equals(type);
    }

    public boolean isRicos() {
        return "ricos".equals(type);
    }

    /** 1 = primeiro lugar */
    public int getPosition() {
        return position;
    }

    /** Quem estava na posicao (null se estava vazia). */
    public String getOldName() {
        return oldName;
    }

    public String getOldUuid() {
        return oldUuid == null ? null : oldUuid.toString();
    }

    /** Quem esta agora na posicao (null se ficou vazia). */
    public String getNewName() {
        return newName;
    }

    public String getNewUuid() {
        return newUuid == null ? null : newUuid.toString();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
