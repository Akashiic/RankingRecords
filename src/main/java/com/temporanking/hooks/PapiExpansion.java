package com.temporanking.hooks;

import com.temporanking.TempoRanking;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Expansao do PlaceholderAPI (para TAB, scoreboard, chat...): %temporanking_<chave>%
 * Ex.: %temporanking_tempo_top_1_nome%, %temporanking_tempo_meu_total%
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final TempoRanking plugin;

    public PapiExpansion(TempoRanking plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() {
        return "temporanking";
    }

    @Override public String getAuthor() {
        return "TempoRanking";
    }

    @Override public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override public boolean persist() {
        return true;
    }

    @Override public boolean canRegister() {
        return true;
    }

    @Override public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return null;
        return plugin.placeholders().resolve(params.toLowerCase(),
                player == null ? null : player.getUniqueId(),
                player == null ? null : player.getName());
    }
}
