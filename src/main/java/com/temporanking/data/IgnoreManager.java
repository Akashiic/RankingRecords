package com.temporanking.data;

import com.temporanking.Settings;
import com.temporanking.TempoRanking;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Decide quem fica fora dos rankings: OPs, quem tem a permissao temporanking.ignorar
 * (ou .tempo / .ricos) e quem esta num dos grupos da config.
 *
 * Permissao e grupo so podem ser lidos direto de jogadores online. Para os offline o
 * resultado fica em cache (ignorados.yml) e e reconsultado de tempos em tempos numa thread
 * assincrona via Vault/PlaceholderAPI (o LuckPerms recusa consulta offline na thread principal).
 */
public final class IgnoreManager {

    public static final String PERM_ALL = "temporanking.ignorar";
    public static final String PERM_TIME = "temporanking.ignorar.tempo";
    public static final String PERM_MONEY = "temporanking.ignorar.ricos";

    private static final Pattern SPLIT = Pattern.compile("[,;|\\s]+");

    /** Consulta de permissao/grupo de jogador offline (Vault). Pode ser chamada fora da thread principal. */
    public interface OfflinePerms {
        boolean has(OfflinePlayer p, String permission);

        Collection<String> groups(OfflinePlayer p);

        Collection<String> groups(Player p);
    }

    /** Le um placeholder do PAPI para o jogador. */
    public interface PlaceholderReader {
        String read(OfflinePlayer p, String text);
    }

    public static final class Flags {
        public final boolean time;
        public final boolean money;
        public final String reason;
        final long checkedAt;

        Flags(boolean time, boolean money, String reason, long checkedAt) {
            this.time = time;
            this.money = money;
            this.reason = reason;
            this.checkedAt = checkedAt;
        }
    }

    private final TempoRanking plugin;
    private final File file;
    private final Map<UUID, Flags> cache = new ConcurrentHashMap<UUID, Flags>();
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private volatile Set<UUID> ops = Collections.emptySet();
    private OfflinePerms perms;
    private PlaceholderReader papi;

    public IgnoreManager(TempoRanking plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ignorados.yml");
    }

    public void setPerms(OfflinePerms perms) {
        this.perms = perms;
    }

    public void setPapi(PlaceholderReader papi) {
        this.papi = papi;
    }

    // ---------------------------------------------------------------- consulta

    public boolean isIgnoredTime(UUID id) {
        if (plugin.settings().ignoreOps && ops.contains(id)) return true;
        Flags f = cache.get(id);
        return f != null && f.time;
    }

    public boolean isIgnoredMoney(UUID id) {
        if (plugin.settings().ignoreOps && ops.contains(id)) return true;
        Flags f = cache.get(id);
        return f != null && f.money;
    }

    /** Motivo legivel (para /temporanking ignorados), ou null se nao esta ignorado. */
    public String reason(UUID id) {
        if (plugin.settings().ignoreOps && ops.contains(id)) return "op";
        Flags f = cache.get(id);
        return f != null && (f.time || f.money) ? f.reason : null;
    }

    // ------------------------------------------------------------ atualizacao

    /** Thread principal: recarrega a lista de OPs (ops.json). */
    public void refreshOps() {
        Set<UUID> set = new HashSet<UUID>();
        try {
            for (OfflinePlayer op : Bukkit.getOperators()) {
                if (op != null && op.getUniqueId() != null) set.add(op.getUniqueId());
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Nao foi possivel ler a lista de OPs", t);
        }
        ops = Collections.unmodifiableSet(set);
    }

    /** Thread principal: reavalia os jogadores online (permissao e grupo sao lidos ao vivo). */
    public void checkOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) checkOnline(p);
    }

    public void checkOnline(Player p) {
        Settings s = plugin.settings();
        boolean all = p.hasPermission(PERM_ALL);
        boolean time = all || p.hasPermission(PERM_TIME);
        boolean money = all || p.hasPermission(PERM_MONEY);
        String reason = time || money ? "permissao" : null;
        if (!(time && money) && !s.ignoreGroups.isEmpty()) {
            Set<String> groups = new HashSet<String>();
            if (s.groupViaVault && perms != null) {
                try {
                    addAll(groups, perms.groups(p));
                } catch (Throwable ignored) {
                }
            }
            if (papi != null && s.groupPlaceholder.length() > 0) addSplit(groups, papi.read(p, s.groupPlaceholder));
            String g = firstMatch(groups, s.ignoreGroups);
            if (g != null) {
                time = money = true;
                reason = "grupo " + g;
            }
        }
        cache.put(p.getUniqueId(), new Flags(time, money, reason, System.currentTimeMillis()));
    }

    /**
     * Thread principal: agenda a verificacao assincrona dos jogadores offline cujo cache
     * esta vencido. Ao terminar, chama {@code done} na thread principal.
     *
     * @param force ignora a idade do cache
     */
    public void checkOffline(Collection<UUID> candidates, boolean force, final Runnable done) {
        final Settings s = plugin.settings();
        if (perms == null && (papi == null || s.groupPlaceholder.length() == 0)) {
            if (done != null) done.run();
            return;
        }
        if (!checking.compareAndSet(false, true)) return;
        long maxAge = s.offlineCheckMinutes * 60000L;
        long now = System.currentTimeMillis();
        final List<OfflinePlayer> todo = new ArrayList<OfflinePlayer>();
        for (UUID id : candidates) {
            if (Bukkit.getPlayer(id) != null) continue; // online e reavaliado ao vivo
            Flags f = cache.get(id);
            if (!force && f != null && now - f.checkedAt < maxAge) continue;
            todo.add(Bukkit.getOfflinePlayer(id));
        }
        if (todo.isEmpty()) {
            checking.set(false);
            if (done != null) done.run();
            return;
        }
        final OfflinePerms p = perms;
        final PlaceholderReader ph = papi;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                int errors = 0;
                try {
                    for (OfflinePlayer op : todo) {
                        try {
                            boolean all = p != null && p.has(op, PERM_ALL);
                            boolean time = all || (p != null && p.has(op, PERM_TIME));
                            boolean money = all || (p != null && p.has(op, PERM_MONEY));
                            String reason = time || money ? "permissao" : null;
                            if (!(time && money) && !s.ignoreGroups.isEmpty()) {
                                Set<String> groups = new HashSet<String>();
                                if (s.groupViaVault && p != null) addAll(groups, p.groups(op));
                                if (ph != null && s.groupPlaceholder.length() > 0) {
                                    addSplit(groups, ph.read(op, s.groupPlaceholder));
                                }
                                String g = firstMatch(groups, s.ignoreGroups);
                                if (g != null) {
                                    time = money = true;
                                    reason = "grupo " + g;
                                }
                            }
                            cache.put(op.getUniqueId(), new Flags(time, money, reason, System.currentTimeMillis()));
                        } catch (Throwable t) {
                            if (errors++ == 0) {
                                plugin.getLogger().log(Level.WARNING, "Erro ao verificar permissao/grupo offline de "
                                        + op.getUniqueId() + " (mostrando so o primeiro)", t);
                            }
                        }
                    }
                } finally {
                    checking.set(false);
                    if (done != null) Bukkit.getScheduler().runTask(plugin, done);
                }
            }
        });
    }

    public boolean isChecking() {
        return checking.get();
    }

    /** Todos os UUIDs ignorados em algum ranking (para listar). */
    public Set<UUID> ignoredIds() {
        Set<UUID> out = new HashSet<UUID>();
        if (plugin.settings().ignoreOps) out.addAll(ops);
        for (Map.Entry<UUID, Flags> e : cache.entrySet()) {
            if (e.getValue().time || e.getValue().money) out.add(e.getKey());
        }
        return out;
    }

    // ----------------------------------------------------------------- helpers

    private static void addAll(Set<String> out, Collection<String> in) {
        if (in == null) return;
        for (String g : in) if (g != null) out.add(clean(g));
    }

    private static void addSplit(Set<String> out, String value) {
        if (value == null) return;
        for (String g : SPLIT.split(clean(value))) if (g.length() > 0) out.add(g);
    }

    private static String clean(String s) {
        return ChatColor.stripColor(s).trim().toLowerCase(Locale.ROOT);
    }

    private static String firstMatch(Set<String> groups, Set<String> ignored) {
        for (String g : groups) if (ignored.contains(g)) return g;
        return null;
    }

    // -------------------------------------------------------------- persistencia

    public void load() {
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            ConfigurationSection sec = yml.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                cache.put(UUID.fromString(key), new Flags(sec.getBoolean("tempo"), sec.getBoolean("ricos"),
                        sec.getString("motivo"), sec.getLong("verificado")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** Thread principal: monta o YAML; a gravacao pode ser assincrona. */
    public void save(boolean async) {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Flags> e : cache.entrySet()) {
            Flags f = e.getValue();
            String k = e.getKey().toString();
            yml.set(k + ".tempo", f.time);
            yml.set(k + ".ricos", f.money);
            if (f.reason != null) yml.set(k + ".motivo", f.reason);
            yml.set(k + ".verificado", f.checkedAt);
        }
        final String data = yml.saveToString();
        Runnable write = new Runnable() {
            @Override public void run() {
                synchronized (IgnoreManager.this) {
                    try {
                        File dir = file.getParentFile();
                        if (!dir.exists()) dir.mkdirs();
                        File tmp = new File(dir, "ignorados.yml.tmp");
                        java.nio.file.Files.write(tmp.toPath(), data.getBytes("UTF-8"));
                        java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        plugin.getLogger().log(Level.WARNING, "Erro ao salvar ignorados.yml", ex);
                    }
                }
            }
        };
        if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }
}
