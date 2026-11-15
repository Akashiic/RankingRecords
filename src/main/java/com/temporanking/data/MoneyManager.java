package com.temporanking.data;

import com.temporanking.TempoRanking;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Ranking dos mais ricos. O Vault nao tem "top saldos", entao o ranking e montado a partir
 * dos arquivos de userdata do Essentials (lidos em thread assincrona) e o saldo de quem
 * esta online vem ao vivo do Vault.
 */
public final class MoneyManager {

    /** Le saldo pelo Vault. Implementado em hooks.VaultHook. */
    public interface BalanceSource {
        BigDecimal getBalance(OfflinePlayer player);
    }

    private final TempoRanking plugin;
    private final Map<UUID, BigDecimal> onlineBalances = new ConcurrentHashMap<UUID, BigDecimal>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile Ranking<BigDecimal> ranking = Ranking.empty();
    /** todas as contas vistas na ultima leitura (inclusive as ignoradas), para checar permissao offline */
    private volatile Set<UUID> lastIds = new HashSet<UUID>();
    /** saldo de todas as contas lidas, inclusive as ignoradas (para posicao/saldo de quem esta fora do top) */
    private volatile Map<UUID, BigDecimal> allBalances = new HashMap<UUID, BigDecimal>();
    private BalanceSource source;

    public MoneyManager(TempoRanking plugin) {
        this.plugin = plugin;
    }

    public void setSource(BalanceSource source) {
        this.source = source;
    }

    public boolean hasSource() {
        return source != null;
    }

    /** Thread principal, a cada segundo: atualiza o saldo de quem esta online. */
    public void updateOnline() {
        if (source == null) return;
        Set<UUID> online = new HashSet<UUID>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            try {
                BigDecimal b = source.getBalance(p);
                if (b != null) onlineBalances.put(p.getUniqueId(), b);
            } catch (Throwable ignored) {
            }
        }
        onlineBalances.keySet().retainAll(online);
    }

    /** Thread principal. Agenda a releitura do userdata e recalcula o ranking. */
    public void refresh(final File userdataDir, final BigDecimal startingBalance,
                        final boolean includeNpc, final Set<String> ignoredLower,
                        final IgnoreManager ignore, final Runnable done) {
        if (!refreshing.compareAndSet(false, true)) return;
        updateOnline();
        final Map<UUID, BigDecimal> online = new HashMap<UUID, BigDecimal>(onlineBalances);
        final Map<UUID, String> onlineNames = new HashMap<UUID, String>();
        for (Player p : Bukkit.getOnlinePlayers()) onlineNames.put(p.getUniqueId(), p.getName());

        if (userdataDir == null || !userdataDir.isDirectory()) {
            // Sem Essentials: usa os jogadores que o servidor conhece, via Vault (thread principal)
            try {
                List<Ranking.Entry<BigDecimal>> list = new ArrayList<Ranking.Entry<BigDecimal>>();
                if (source != null) {
                    for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                        String name = op.getName();
                        if (name == null) continue;
                        BigDecimal b = online.get(op.getUniqueId());
                        if (b == null) {
                            try { b = source.getBalance(op); } catch (Throwable ignored) { }
                        }
                        if (b != null) list.add(new Ranking.Entry<BigDecimal>(op.getUniqueId(), name, b));
                    }
                }
                publish(list, online, onlineNames, ignoredLower, ignore);
            } finally {
                refreshing.set(false);
                if (done != null) done.run();
            }
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try {
                    List<EssentialsUserdata.Account> accounts = EssentialsUserdata.readAll(userdataDir, startingBalance);
                    List<Ranking.Entry<BigDecimal>> list = new ArrayList<Ranking.Entry<BigDecimal>>(accounts.size());
                    for (EssentialsUserdata.Account a : accounts) {
                        if (a.npc && !includeNpc) continue;
                        list.add(new Ranking.Entry<BigDecimal>(a.uuid, a.name, a.money));
                    }
                    publish(list, online, onlineNames, ignoredLower, ignore);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Erro ao montar o ranking de dinheiro", t);
                } finally {
                    refreshing.set(false);
                    if (done != null) Bukkit.getScheduler().runTask(plugin, done);
                }
            }
        });
    }

    private void publish(List<Ranking.Entry<BigDecimal>> fromFiles, Map<UUID, BigDecimal> online,
                         Map<UUID, String> onlineNames, Set<String> ignoredLower, IgnoreManager ignore) {
        Set<UUID> seen = new HashSet<UUID>();
        for (Ranking.Entry<BigDecimal> e : fromFiles) seen.add(e.uuid);
        seen.addAll(online.keySet());
        lastIds = seen;
        Map<UUID, Ranking.Entry<BigDecimal>> byId = new HashMap<UUID, Ranking.Entry<BigDecimal>>();
        for (Ranking.Entry<BigDecimal> e : fromFiles) byId.put(e.uuid, e);
        // saldo ao vivo de quem esta online tem prioridade sobre o arquivo
        for (Map.Entry<UUID, BigDecimal> e : online.entrySet()) {
            String name = onlineNames.get(e.getKey());
            Ranking.Entry<BigDecimal> old = byId.get(e.getKey());
            if (name == null && old != null) name = old.name;
            if (name == null) continue;
            byId.put(e.getKey(), new Ranking.Entry<BigDecimal>(e.getKey(), name, e.getValue()));
        }
        Map<UUID, BigDecimal> balances = new HashMap<UUID, BigDecimal>(byId.size() * 2);
        for (Ranking.Entry<BigDecimal> e : byId.values()) balances.put(e.uuid, e.value);
        allBalances = balances;
        List<Ranking.Entry<BigDecimal>> list = new ArrayList<Ranking.Entry<BigDecimal>>(byId.size());
        for (Ranking.Entry<BigDecimal> e : byId.values()) {
            if (ignoredLower.contains(e.name.toLowerCase(Locale.ROOT))) continue;
            if (ignore != null && ignore.isIgnoredMoney(e.uuid)) continue;
            list.add(e);
        }
        Collections.sort(list, new Comparator<Ranking.Entry<BigDecimal>>() {
            @Override public int compare(Ranking.Entry<BigDecimal> a, Ranking.Entry<BigDecimal> b) {
                int c = b.value.compareTo(a.value);
                return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
            }
        });
        this.ranking = new Ranking<BigDecimal>(list);
    }

    public Set<UUID> knownIds() {
        return lastIds;
    }

    public Ranking<BigDecimal> getRanking() {
        return ranking;
    }

    /** Saldo ao vivo se online; senao o da ultima leitura do ranking; null se desconhecido. */
    public BigDecimal getBalance(UUID id) {
        BigDecimal b = onlineBalances.get(id);
        if (b != null) return b;
        return allBalances.get(id);
    }

    public void clearOnline(UUID id) {
        onlineBalances.remove(id);
    }
}
