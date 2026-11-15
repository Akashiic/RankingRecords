package com.temporanking.hooks;

import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import com.gmail.filoghost.holographicdisplays.api.placeholder.PlaceholderReplacer;
import com.temporanking.Placeholders;
import com.temporanking.TempoRanking;

/**
 * Registra os placeholders no Holographic Displays 2.2.6 (API com.gmail.filoghost).
 *
 * O HD 2.2.6 so tem placeholders globais. Para os placeholders do proprio jogador o
 * replacer devolve um marcador ({TR:chave:0}/{TR:chave:1}, alternando a cada atualizacao
 * para o HD reenviar o nome da entidade) e o {@link PacketHook} troca o marcador pelo valor
 * de cada jogador no pacote que vai para ele.
 */
public final class HologramsHook {

    private final TempoRanking plugin;
    private int registered;
    private boolean perPlayer;
    private final java.util.Set<String> papiRegistered = new java.util.HashSet<String>();

    public HologramsHook(TempoRanking plugin) {
        this.plugin = plugin;
    }

    public void register(boolean perPlayer) {
        HologramsAPI.unregisterPlaceholders(plugin);
        registered = 0;
        this.perPlayer = perPlayer;
        papiRegistered.clear();
        final Placeholders ph = plugin.placeholders();
        double rate = plugin.settings().hologramRefresh;

        for (final String key : ph.globalKeys()) {
            add(key, rate, new PlaceholderReplacer() {
                @Override public String update() {
                    String v = ph.resolve(key, null, null);
                    return v == null ? "" : v;
                }
            });
        }

        for (final String key : Placeholders.PERSONAL) addPersonal(key, rate);
        for (String papiKey : plugin.papiKeys()) registerPapi(papiKey);
        plugin.getLogger().info("Holographic Displays: " + registered + " placeholders registrados"
                + (perPlayer ? " (por jogador via ProtocolLib ativo)." : "."));
    }

    /**
     * Registra {papi:chave} no HD (se ainda nao estiver). Precisa acontecer antes do HD criar a
     * linha, senao ela so passa a atualizar depois de /hd reload.
     */
    public void registerPapi(String papiKey) {
        if (!papiRegistered.add(papiKey)) return;
        addPersonal(Placeholders.PAPI_PREFIX + papiKey, plugin.settings().hologramRefresh);
    }

    private void addPersonal(final String key, double rate) {
            if (perPlayer) {
                add(key, rate, new PlaceholderReplacer() {
                    private int toggle;

                    @Override public String update() {
                        toggle ^= 1;
                        return PacketHook.marker(key, toggle);
                    }
                });
            } else {
                final String fallback = plugin.settings().noProtocolLib;
                add(key, rate, new PlaceholderReplacer() {
                    @Override public String update() {
                        return fallback;
                    }
                });
            }
    }

    private void add(String key, double rate, PlaceholderReplacer replacer) {
        String text = "{" + key + "}";
        HologramsAPI.unregisterPlaceholder(plugin, text);
        if (HologramsAPI.registerPlaceholder(plugin, text, rate, replacer)) {
            registered++;
        } else {
            plugin.getLogger().warning("Placeholder " + text + " ja estava registrado por outro plugin.");
        }
    }

    public void unregister() {
        try {
            HologramsAPI.unregisterPlaceholders(plugin);
        } catch (Throwable ignored) {
        }
    }
}
