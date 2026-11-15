package com.temporanking.hooks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.temporanking.Placeholders;
import com.temporanking.TempoRanking;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Troca os marcadores {TR:chave:N} pelo valor do jogador que vai receber o pacote.
 *
 * No 1.7.10 a linha de texto do HD e um cavalo com nome customizado (DataWatcher indice 10),
 * enviado em SPAWN_ENTITY_LIVING (spawn) e ENTITY_METADATA (atualizacoes). O mesmo objeto de
 * pacote e enviado para todos os jogadores, por isso o pacote e clonado antes de alterar.
 */
public final class PacketHook {

    static final String PREFIX = "{TR:";
    private static final Pattern MARKER = Pattern.compile("\\{TR:([^{}]+):\\d\\}");

    private final TempoRanking plugin;
    private PacketAdapter adapter;
    private volatile boolean warnedMeta;
    private volatile boolean warnedSpawn;

    public PacketHook(TempoRanking plugin) {
        this.plugin = plugin;
    }

    static String marker(String key, int toggle) {
        return PREFIX + key + ":" + toggle + "}";
    }

    public void register() {
        adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.ENTITY_METADATA, PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) return;
                Player viewer = event.getPlayer();
                if (viewer == null) return;
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                    try {
                        handleMetadata(event, viewer);
                    } catch (Throwable t) {
                        if (!warnedMeta) {
                            warnedMeta = true;
                            plugin.getLogger().log(Level.WARNING, "Falha ao personalizar ENTITY_METADATA (avisando so uma vez)", t);
                        }
                    }
                } else {
                    try {
                        handleSpawn(event, viewer);
                    } catch (Throwable t) {
                        if (!warnedSpawn) {
                            warnedSpawn = true;
                            plugin.getLogger().log(Level.WARNING, "Falha ao personalizar SPAWN_ENTITY_LIVING (avisando so uma vez)", t);
                        }
                    }
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
    }

    public void unregister() {
        if (adapter != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
            } catch (Throwable ignored) {
            }
            adapter = null;
        }
    }

    private void handleMetadata(PacketEvent event, Player viewer) {
        PacketContainer packet = event.getPacket();
        List<WrappedWatchableObject> list = packet.getWatchableCollectionModifier().read(0);
        if (list == null || list.isEmpty()) return;
        List<WrappedWatchableObject> out = null;
        for (int i = 0; i < list.size(); i++) {
            WrappedWatchableObject w = list.get(i);
            if (w == null) continue;
            Object v = w.getValue();
            if (v instanceof String && ((String) v).indexOf(PREFIX) >= 0) {
                if (out == null) out = new ArrayList<WrappedWatchableObject>(list);
                out.set(i, new WrappedWatchableObject(w.getIndex(), replace((String) v, viewer)));
            }
        }
        if (out == null) return;
        PacketContainer copy = packet.shallowClone();
        copy.getWatchableCollectionModifier().write(0, out);
        event.setPacket(copy);
    }

    private void handleSpawn(PacketEvent event, Player viewer) {
        PacketContainer packet = event.getPacket();
        WrappedDataWatcher dw = packet.getDataWatcherModifier().read(0);
        if (dw == null) return;
        WrappedDataWatcher copyDw = null;
        for (WrappedWatchableObject w : dw.getWatchableObjects()) {
            if (w == null) continue;
            Object v = w.getValue();
            if (v instanceof String && ((String) v).indexOf(PREFIX) >= 0) {
                if (copyDw == null) copyDw = dw.deepClone();
                copyDw.setObject(w.getIndex(), replace((String) v, viewer));
            }
        }
        if (copyDw == null) return;
        PacketContainer copy = packet.shallowClone();
        copy.getDataWatcherModifier().write(0, copyDw);
        event.setPacket(copy);
    }

    private String replace(String text, Player viewer) {
        UUID id = viewer.getUniqueId();
        String name = viewer.getName();
        Placeholders ph = plugin.placeholders();
        Matcher m = MARKER.matcher(text);
        StringBuffer sb = new StringBuffer(text.length() + 16);
        while (m.find()) {
            String v = ph.resolve(m.group(1), id, name);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
