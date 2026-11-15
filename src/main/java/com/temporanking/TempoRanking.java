package com.temporanking;

import com.temporanking.data.IgnoreManager;
import com.temporanking.data.MoneyManager;
import com.temporanking.data.PlaytimeManager;
import com.temporanking.data.VanillaImporter;
import com.temporanking.hooks.EssentialsHook;
import com.temporanking.hooks.HologramsHook;
import com.temporanking.hooks.PacketHook;
import com.temporanking.hooks.PapiBridge;
import com.temporanking.hooks.PapiExpansion;
import com.temporanking.hooks.VaultHook;
import com.temporanking.hooks.VaultPermsHook;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.logging.Level;

public final class TempoRanking extends JavaPlugin implements Listener {

    private volatile Settings settings;
    private Placeholders placeholders;
    private PlaytimeManager playtime;
    private MoneyManager money;
    private IgnoreManager ignore;
    private PapiBridge papiBridge;

    private EssentialsHook essentials;
    private HologramsHook holograms;
    private PacketHook packets;
    private PapiExpansion papi;

    private final List<BukkitTask> tasks = new ArrayList<BukkitTask>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        settings = new Settings(getConfig());
        placeholders = new Placeholders(this);
        playtime = new PlaytimeManager(this);
        money = new MoneyManager(this);
        ignore = new IgnoreManager(this);
        playtime.load();
        ignore.load();
        ignore.refreshOps();

        hookPlugins();

        getServer().getPluginManager().registerEvents(this, this);
        Commands cmd = new Commands(this);
        getCommand("tempo").setExecutor(cmd);
        getCommand("ricos").setExecutor(cmd);
        getCommand("temporanking").setExecutor(cmd);

        // jogadores ja online (ex.: /reload)
        for (Player p : Bukkit.getOnlinePlayers()) playtime.join(p);

        startTasks();

        if (settings.importVanilla && !playtime.isImportDone()) {
            // espera o servidor terminar de carregar os mundos
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override public void run() {
                    runVanillaImport(null);
                }
            }, 40L);
        }
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        if (packets != null) packets.unregister();
        if (holograms != null) holograms.unregister();
        if (papi != null) {
            try { papi.unregister(); } catch (Throwable ignored) { }
        }
        if (playtime != null) {
            playtime.flushOnline();
            playtime.save(false);
        }
        if (ignore != null) ignore.save(false);
    }

    // ------------------------------------------------------------------ hooks

    private void hookPlugins() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess != null && ess.isEnabled()) {
            try {
                essentials = new EssentialsHook(ess);
                getLogger().info("Essentials encontrado (AFK e userdata para o ranking de dinheiro).");
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Falha ao conectar no Essentials", t);
            }
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                VaultHook vh = VaultHook.create();
                if (vh != null) {
                    money.setSource(vh);
                    getLogger().info("Vault conectado a economia: " + vh.getName());
                } else {
                    getLogger().warning("Vault sem provedor de economia! O saldo online nao sera ao vivo.");
                }
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Falha ao conectar no Vault", t);
            }
            try {
                VaultPermsHook ph = VaultPermsHook.create();
                if (ph != null) {
                    ignore.setPerms(ph);
                    getLogger().info("Vault conectado as permissoes: " + ph.getName());
                }
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Falha ao conectar nas permissoes do Vault", t);
            }
        }

        // PAPI antes do HD: as chaves {papi:...} precisam estar registradas quando o HD criar as linhas
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papiBridge = new PapiBridge();
                ignore.setPapi(papiBridge);
                loadPapiKeys();
            } catch (Throwable t) {
                papiBridge = null;
                getLogger().log(Level.WARNING, "Falha ao conectar no PlaceholderAPI", t);
            }
        }

        boolean perPlayer = false;
        if (settings.perPlayer && Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                packets = new PacketHook(this);
                packets.register();
                perPlayer = true;
            } catch (Throwable t) {
                packets = null;
                getLogger().log(Level.WARNING, "Falha ao registrar o listener do ProtocolLib; placeholders por jogador desativados", t);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays")) {
            try {
                holograms = new HologramsHook(this);
                holograms.register(perPlayer);
            } catch (Throwable t) {
                holograms = null;
                getLogger().log(Level.WARNING, "Falha ao registrar placeholders no Holographic Displays", t);
            }
        } else {
            getLogger().warning("Holographic Displays nao encontrado.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papi = new PapiExpansion(this);
                if (papi.register()) getLogger().info("PlaceholderAPI: expansao %temporanking_...% registrada.");
            } catch (Throwable t) {
                papi = null;
                getLogger().log(Level.WARNING, "Falha ao registrar no PlaceholderAPI", t);
            }
        }
    }

    /** Chaves {papi:...} da config e as que ja estao usadas nos hologramas (database.yml do HD). */
    private void loadPapiKeys() {
        if (papiBridge == null) return;
        for (String k : settings.papiHologramKeys) {
            if (k != null && k.trim().length() > 0) papiBridge.addKey(k.trim().replace("%", ""));
        }
        Plugin hd = Bukkit.getPluginManager().getPlugin("HolographicDisplays");
        File db = new File(hd != null ? hd.getDataFolder() : new File(getDataFolder().getParentFile(), "HolographicDisplays"),
                "database.yml");
        for (String k : PapiBridge.findTags(db)) papiBridge.addKey(k);
        if (!papiBridge.keys().isEmpty()) {
            getLogger().info("PlaceholderAPI nos hologramas: " + papiBridge.keys());
        }
    }

    // ------------------------------------------------------------------ tasks

    private void startTasks() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        final Settings s = settings;

        // contagem de tempo + saldo ao vivo, a cada segundo
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() {
                playtime.tick(settings.countAfk, essentials);
                money.updateOnline();
                if (papiBridge != null) papiBridge.tick();
            }
        }, 20L, 20L));

        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() {
                ignore.refreshOps();
                ignore.checkOnline();
                playtime.rebuildRanking(settings.timeIgnored, ignore);
            }
        }, 1L, s.timeRankingSeconds * 20L));

        // permissao/grupo de quem esta offline (so reconsulta quem esta com o cache vencido)
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() {
                checkOffline(false, null);
            }
        }, 200L, 60L * 20L));

        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() {
                refreshMoney(null);
            }
        }, 20L, s.moneyRankingSeconds * 20L));

        long save = s.saveMinutes * 60L * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override public void run() {
                playtime.save(true);
                ignore.save(true);
            }
        }, save, save));
    }

    void refreshMoney(Runnable done) {
        Settings s = settings;
        money.refresh(essentialsUserdataDir(), essentialsStartingBalance(), s.includeNpc, s.moneyIgnored, ignore, done);
    }

    void checkOffline(boolean force, final Runnable done) {
        Set<UUID> ids = new HashSet<UUID>(playtime.knownIds());
        ids.addAll(money.knownIds());
        ignore.checkOffline(ids, force, new Runnable() {
            @Override public void run() {
                playtime.rebuildRanking(settings.timeIgnored, ignore);
                refreshMoney(done);
                ignore.save(true);
            }
        });
    }

    private File essentialsDir() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        return ess != null ? ess.getDataFolder() : new File(getDataFolder().getParentFile(), "Essentials");
    }

    private File essentialsUserdataDir() {
        File dir = new File(essentialsDir(), "userdata");
        return dir.isDirectory() ? dir : null;
    }

    private BigDecimal cachedStartingBalance;

    private BigDecimal essentialsStartingBalance() {
        if (cachedStartingBalance == null) {
            BigDecimal b = BigDecimal.ZERO;
            File cfg = new File(essentialsDir(), "config.yml");
            if (cfg.isFile()) {
                try {
                    String v = YamlConfiguration.loadConfiguration(cfg).getString("starting-balance", "0");
                    b = new BigDecimal(v.trim());
                } catch (Exception ignored) {
                }
            }
            cachedStartingBalance = b;
        }
        return cachedStartingBalance;
    }

    // ------------------------------------------------------------- importacao

    void runVanillaImport(final CommandSender requester) {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        final File statsDir = main == null ? null : new File(main.getWorldFolder(), "stats");
        final File usercache = new File("usercache.json");
        final File userdata = essentialsUserdataDir();
        if (statsDir == null || !statsDir.isDirectory()) {
            getLogger().info("Pasta de estatisticas vanilla nao encontrada (" + statsDir + "), importacao ignorada.");
            playtime.applyImport(new java.util.HashMap<java.util.UUID, Long>(), new java.util.HashMap<java.util.UUID, String>());
            if (requester != null) requester.sendMessage(settings.prefix() + settings.msg("importado").replace("{quantidade}", "0"));
            return;
        }
        if (requester != null) requester.sendMessage(settings.prefix() + settings.msg("importando"));
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override public void run() {
                final VanillaImporter imp = new VanillaImporter();
                imp.readStats(statsDir);
                imp.readUserCache(usercache);
                imp.readEssentialsNames(userdata);
                Bukkit.getScheduler().runTask(TempoRanking.this, new Runnable() {
                    @Override public void run() {
                        int changed = playtime.applyImport(imp.ticks, imp.names);
                        playtime.rebuildRanking(settings.timeIgnored, ignore);
                        playtime.save(true);
                        getLogger().info("Importacao vanilla: " + imp.filesRead + " arquivos lidos, "
                                + changed + " jogadores atualizados.");
                        if (requester != null) {
                            requester.sendMessage(settings.prefix() + settings.msg("importado")
                                    .replace("{quantidade}", String.valueOf(changed)));
                        }
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------- reload

    void reloadAll() {
        reloadConfig();
        settings = new Settings(getConfig());
        cachedStartingBalance = null;
        loadPapiKeys();
        ignore.refreshOps();
        ignore.checkOnline();
        if (holograms != null) {
            try {
                holograms.register(packets != null);
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Falha ao registrar placeholders no Holographic Displays", t);
            }
        }
        startTasks();
        playtime.rebuildRanking(settings.timeIgnored, ignore);
        refreshMoney(null);
        checkOffline(true, null);
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        playtime.join(e.getPlayer());
        ignore.checkOnline(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        playtime.quit(e.getPlayer());
        money.clearOnline(e.getPlayer().getUniqueId());
        if (papiBridge != null) papiBridge.clear(e.getPlayer().getUniqueId());
    }

    // Registra {papi:...} usados em comandos do HD ANTES do HD executar o comando,
    // para a linha nova ja nascer com o placeholder ativo.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        hologramCommand(e.getMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent e) {
        hologramCommand(e.getCommand());
    }

    private void hologramCommand(String cmd) {
        if (papiBridge == null || holograms == null || cmd == null || cmd.indexOf("{papi:") < 0) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        int sp = c.indexOf(' ');
        String label = (sp < 0 ? c : c.substring(0, sp)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!label.equals("hd") && !label.equals("holo") && !label.equals("hologram")
                && !label.equals("holograms") && !label.equals("holographicdisplays")) return;
        for (String k : PapiBridge.findTags(c)) {
            if (papiBridge.addKey(k)) {
                try {
                    holograms.registerPapi(k);
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "Falha ao registrar {papi:" + k + "}", t);
                }
            }
        }
    }

    // ------------------------------------------------------------------ getters

    public Settings settings() {
        return settings;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    public PlaytimeManager playtime() {
        return playtime;
    }

    public MoneyManager money() {
        return money;
    }

    public IgnoreManager ignore() {
        return ignore;
    }

    public Collection<String> papiKeys() {
        return papiBridge == null ? Collections.<String>emptySet() : papiBridge.keys();
    }

    String papiValue(UUID viewer, String key) {
        return papiBridge == null ? "" : papiBridge.get(viewer, key);
    }

    boolean perPlayerActive() {
        return packets != null;
    }
}
