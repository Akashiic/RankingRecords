package com.temporanking.hooks;

import com.temporanking.data.IgnoreManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ponte com o PlaceholderAPI. So e carregada se o PAPI estiver instalado.
 *
 * Nos hologramas, {papi:identificador_parametro} vira %identificador_parametro% resolvido para
 * quem esta vendo. Os valores sao calculados na thread principal (expansoes do PAPI nao sao
 * necessariamente thread-safe) e guardados em cache para o listener de pacotes ler.
 */
public final class PapiBridge implements IgnoreManager.PlaceholderReader {

    public static final Pattern HOLO_TAG = Pattern.compile("\\{papi:([^{}%]+)\\}");

    private final Set<String> keys = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<UUID, Map<String, String>> values = new ConcurrentHashMap<UUID, Map<String, String>>();

    @Override
    public String read(OfflinePlayer p, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(p, text);
        } catch (Throwable t) {
            return "";
        }
    }

    /** @return true se a chave e nova */
    public boolean addKey(String key) {
        return keys.add(key.trim());
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(keys);
    }

    /** Thread principal: recalcula os valores de todos os jogadores online. */
    public void tick() {
        if (keys.isEmpty()) {
            values.clear();
            return;
        }
        Set<UUID> online = new HashSet<UUID>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            Map<String, String> m = new HashMap<String, String>();
            for (String k : keys) m.put(k, compute(p, k));
            values.put(p.getUniqueId(), m);
        }
        values.keySet().retainAll(online);
    }

    public void clear(UUID id) {
        values.remove(id);
    }

    /** Qualquer thread: valor em cache; na thread principal calcula na hora se faltar. */
    public String get(UUID id, String key) {
        Map<String, String> m = values.get(id);
        String v = m == null ? null : m.get(key);
        if (v == null && Bukkit.isPrimaryThread()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) v = compute(p, key);
        }
        return v == null ? "" : v;
    }

    private String compute(Player p, String key) {
        try {
            String v = PlaceholderAPI.setPlaceholders(p, "%" + key + "%");
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Procura {papi:...} em um texto (linha de comando, database.yml). */
    public static Set<String> findTags(String text) {
        Set<String> out = new HashSet<String>();
        if (text == null || text.indexOf("{papi:") < 0) return out;
        Matcher m = HOLO_TAG.matcher(text);
        while (m.find()) out.add(m.group(1).trim());
        return out;
    }

    public static Set<String> findTags(File file) {
        if (file == null || !file.isFile()) return new HashSet<String>();
        try {
            return findTags(new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8")));
        } catch (IOException e) {
            return new HashSet<String>();
        }
    }
}
