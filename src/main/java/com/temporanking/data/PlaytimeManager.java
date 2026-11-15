package com.temporanking.data;

import com.temporanking.TempoRanking;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Conta o tempo jogado. A cada segundo (thread principal) soma o tempo decorrido
 * de cada jogador online ao total dele. Os mapas sao concorrentes porque os valores
 * tambem sao lidos pelo listener de pacotes (ProtocolLib) e pelo PlaceholderAPI.
 */
public final class PlaytimeManager {

    public static final class Record {
        public final UUID uuid;
        public volatile String name;
        public volatile long totalMillis;

        Record(UUID uuid, String name, long totalMillis) {
            this.uuid = uuid;
            this.name = name;
            this.totalMillis = totalMillis;
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final TempoRanking plugin;
    private final File file;
    private final Map<UUID, Record> records = new ConcurrentHashMap<UUID, Record>();
    /** momento em que o jogador entrou (sessao atual) */
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<UUID, Long>();
    /** ultimo momento contabilizado no total */
    private final Map<UUID, Long> lastCount = new ConcurrentHashMap<UUID, Long>();
    private final Object saveLock = new Object();

    private volatile Ranking<Long> ranking = Ranking.empty();
    private volatile boolean importDone;

    public PlaytimeManager(TempoRanking plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dados.yml");
    }

    // ------------------------------------------------------------------ dados

    public void load() {
        records.clear();
        if (!file.exists()) {
            importDone = false;
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        importDone = yml.getBoolean("importacao-vanilla-feita", false);
        ConfigurationSection sec = yml.getConfigurationSection("jogadores");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = sec.getString(key + ".nome", key.substring(0, 8));
                long total = sec.getLong(key + ".tempo", 0L);
                records.put(uuid, new Record(uuid, name, total));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Entrada invalida em dados.yml: " + key);
            }
        }
        plugin.getLogger().info("Tempo jogado carregado de " + records.size() + " jogadores.");
    }

    /** Monta o YAML na thread atual e grava o arquivo (pode ser assincrono). */
    public void save(boolean async) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("importacao-vanilla-feita", importDone);
        for (Record r : records.values()) {
            String base = "jogadores." + r.uuid;
            yml.set(base + ".nome", r.name);
            yml.set(base + ".tempo", r.totalMillis);
        }
        final String data = yml.saveToString();
        Runnable write = new Runnable() {
            @Override public void run() {
                writeAtomic(data);
            }
        };
        if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    private void writeAtomic(String data) {
        synchronized (saveLock) {
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().severe("Nao foi possivel criar " + dir);
                return;
            }
            File tmp = new File(dir, "dados.yml.tmp");
            Writer w = null;
            try {
                w = new OutputStreamWriter(new FileOutputStream(tmp), UTF8);
                w.write(data);
                w.close();
                w = null;
                if (file.exists() && !file.delete()) {
                    plugin.getLogger().warning("Nao foi possivel substituir dados.yml");
                }
                if (!tmp.renameTo(file)) {
                    plugin.getLogger().severe("Falha ao renomear dados.yml.tmp para dados.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erro ao salvar dados.yml", e);
            } finally {
                if (w != null) try { w.close(); } catch (IOException ignored) { }
            }
        }
    }

    // --------------------------------------------------------------- contagem

    public void join(Player p) {
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        Record r = records.get(id);
        if (r == null) {
            records.put(id, new Record(id, p.getName(), 0L));
        } else {
            r.name = p.getName();
        }
        sessionStart.put(id, now);
        lastCount.put(id, now);
    }

    public void quit(Player p) {
        UUID id = p.getUniqueId();
        accumulate(id, System.currentTimeMillis(), true);
        sessionStart.remove(id);
        lastCount.remove(id);
    }

    /** Chamado a cada segundo pela thread principal. */
    public void tick(boolean countAfk, AfkChecker afk) {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!lastCount.containsKey(id)) join(p); // seguranca (ex.: /reload)
            boolean count = countAfk || afk == null || !afk.isAfk(p);
            accumulate(id, now, count);
        }
    }

    private void accumulate(UUID id, long now, boolean count) {
        Long last = lastCount.get(id);
        if (last == null) return;
        long delta = now - last;
        // protege contra relogio voltando ou travamentos absurdos
        if (count && delta > 0 && delta < 5 * 60 * 1000L) {
            Record r = records.get(id);
            if (r != null) r.totalMillis += delta;
        }
        lastCount.put(id, now);
    }

    /** Soma ao total o tempo ainda nao contabilizado dos jogadores online. */
    public void flushOnline() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<UUID>(lastCount.keySet())) accumulate(id, now, true);
    }

    public interface AfkChecker {
        boolean isAfk(Player p);
    }

    // ---------------------------------------------------------------- ranking

    public void rebuildRanking(Set<String> ignoredLower, IgnoreManager ignore) {
        List<Record> list = new ArrayList<Record>(records.size());
        for (Record r : records.values()) {
            if (r.name != null && ignoredLower.contains(r.name.toLowerCase(Locale.ROOT))) continue;
            if (ignore != null && ignore.isIgnoredTime(r.uuid)) continue;
            if (r.totalMillis <= 0) continue;
            list.add(r);
        }
        final Map<Record, Long> snap = new java.util.IdentityHashMap<Record, Long>();
        for (Record r : list) snap.put(r, r.totalMillis);
        Collections.sort(list, new Comparator<Record>() {
            @Override public int compare(Record a, Record b) {
                int c = Long.compare(snap.get(b), snap.get(a));
                return c != 0 ? c : String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name));
            }
        });
        List<Ranking.Entry<Long>> entries = new ArrayList<Ranking.Entry<Long>>(list.size());
        for (Record r : list) entries.add(new Ranking.Entry<Long>(r.uuid, r.name, snap.get(r)));
        this.ranking = new Ranking<Long>(entries);
    }

    public Ranking<Long> getRanking() {
        return ranking;
    }

    // ---------------------------------------------------------------- leitura

    public long getTotal(UUID id) {
        Record r = records.get(id);
        if (r == null) return 0L;
        long total = r.totalMillis;
        // inclui a fracao de segundo ainda nao somada, para o contador andar liso
        Long last = lastCount.get(id);
        if (last != null) {
            long d = System.currentTimeMillis() - last;
            if (d > 0 && d < 5000L) total += d;
        }
        return total;
    }

    /** @return duracao da sessao atual, ou -1 se offline */
    public long getSession(UUID id) {
        Long start = sessionStart.get(id);
        return start == null ? -1L : System.currentTimeMillis() - start;
    }

    public boolean isOnline(UUID id) {
        return sessionStart.containsKey(id);
    }

    public java.util.Collection<UUID> knownIds() {
        return new ArrayList<UUID>(records.keySet());
    }

    public String nameOf(UUID id) {
        Record r = records.get(id);
        return r == null ? null : r.name;
    }

    public Record find(String name) {
        for (Record r : records.values()) {
            if (r.name != null && r.name.equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    public void setTotal(UUID id, String name, long millis) {
        Record r = records.get(id);
        if (r == null) records.put(id, new Record(id, name, Math.max(0, millis)));
        else r.totalMillis = Math.max(0, millis);
    }

    // ----------------------------------------------------- importacao vanilla

    public boolean isImportDone() {
        return importDone;
    }

    /**
     * Aplica o resultado da importacao (thread principal): usa o maior valor entre o
     * que ja temos e o da estatistica vanilla, entao pode ser repetida sem duplicar tempo.
     */
    public int applyImport(Map<UUID, Long> ticksByUuid, Map<UUID, String> names) {
        int changed = 0;
        Set<UUID> seen = new HashSet<UUID>();
        for (Map.Entry<UUID, Long> e : ticksByUuid.entrySet()) {
            UUID id = e.getKey();
            if (!seen.add(id)) continue;
            long millis = e.getValue() * 50L; // 1 tick = 50 ms
            Record r = records.get(id);
            String name = names.get(id);
            if (r == null) {
                if (name == null) continue; // sem nome nao da para mostrar no ranking
                records.put(id, new Record(id, name, millis));
                changed++;
            } else {
                if (millis > r.totalMillis) {
                    r.totalMillis = millis;
                    changed++;
                }
                if ((r.name == null || r.name.length() == 0) && name != null) r.name = name;
            }
        }
        importDone = true;
        return changed;
    }
}
