package com.temporanking;

import com.temporanking.util.MoneyFormat;
import com.temporanking.util.TimeFormat;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Configuracao carregada (imutavel). Trocada inteira no /temporanking reload. */
public final class Settings {

    public final int topSize;

    public final int timeRankingSeconds;
    public final int saveMinutes;
    public final boolean countAfk;
    public final boolean importVanilla;
    public final Set<String> timeIgnored;
    public final TimeFormat time;

    public final int moneyRankingSeconds;
    public final boolean includeNpc;
    public final Set<String> moneyIgnored;
    public final MoneyFormat money;

    public final String lineTime;
    public final String lineMoney;
    public final String emptyName;
    public final String emptyValue;
    public final String emptyLine;
    public final String emptyLineTime;
    public final String noPosition;

    public final double hologramRefresh;
    public final boolean perPlayer;
    public final String noProtocolLib;
    public final List<String> papiHologramKeys;

    public final boolean ignoreOps;
    public final Set<String> ignoreGroups;
    public final boolean groupViaVault;
    public final String groupPlaceholder;
    public final int offlineCheckMinutes;
    public final String ignoredYes;
    public final String ignoredNo;

    private final FileConfiguration cfg;

    Settings(FileConfiguration cfg) {
        this.cfg = cfg;
        this.topSize = clamp(cfg.getInt("tamanho-top", 9), 1, 100);

        this.timeRankingSeconds = clamp(cfg.getInt("tempo.atualizar-ranking-segundos", 5), 1, 3600);
        this.saveMinutes = clamp(cfg.getInt("tempo.salvar-minutos", 5), 1, 1440);
        this.countAfk = cfg.getBoolean("tempo.contar-tempo-afk", true);
        this.importVanilla = cfg.getBoolean("tempo.importar-estatisticas-vanilla", true);
        this.timeIgnored = lowerSet(cfg.getStringList("tempo.ignorar-jogadores"));
        this.time = new TimeFormat(cfg.getConfigurationSection("tempo.formato"));

        this.moneyRankingSeconds = clamp(cfg.getInt("ricos.atualizar-ranking-segundos", 60), 5, 86400);
        this.includeNpc = cfg.getBoolean("ricos.incluir-contas-npc", false);
        this.moneyIgnored = lowerSet(cfg.getStringList("ricos.ignorar-jogadores"));
        this.money = new MoneyFormat(cfg.getConfigurationSection("ricos.formato"));

        this.lineTime = color(cfg.getString("linhas.tempo", "&f{nome} &7- &a{tempo}"));
        this.lineMoney = color(cfg.getString("linhas.rico", "&f{nome} &7- &a{saldo}"));
        this.emptyName = color(cfg.getString("linhas.vazio-nome", "&7---"));
        this.emptyValue = color(cfg.getString("linhas.vazio-valor", "&7---"));
        this.emptyLine = color(cfg.getString("linhas.vazio-linha", "&7---"));
        this.emptyLineTime = color(cfg.getString("linhas.vazio-linha-tempo", "&7---"));
        this.noPosition = color(cfg.getString("linhas.sem-posicao", "-"));

        this.hologramRefresh = Math.max(0.1, cfg.getDouble("hologramas.atualizacao-segundos", 1.0));
        this.perPlayer = cfg.getBoolean("hologramas.por-jogador", true);
        this.noProtocolLib = color(cfg.getString("hologramas.sem-protocollib", "&c(ProtocolLib ausente)"));
        List<String> papiKeys = cfg.getStringList("hologramas.papi-placeholders");
        this.papiHologramKeys = papiKeys == null ? Collections.<String>emptyList() : papiKeys;

        this.ignoreOps = cfg.getBoolean("ignorar.ops", true);
        this.ignoreGroups = lowerSet(cfg.getStringList("ignorar.grupos"));
        this.groupViaVault = cfg.getBoolean("ignorar.grupo-via-vault", true);
        this.groupPlaceholder = cfg.getString("ignorar.grupo-via-placeholder", "%vault_groups%").trim();
        this.offlineCheckMinutes = clamp(cfg.getInt("ignorar.verificar-offline-minutos", 30), 1, 10080);
        this.ignoredYes = color(cfg.getString("linhas.ignorado-sim", "sim"));
        this.ignoredNo = color(cfg.getString("linhas.ignorado-nao", "nao"));
    }

    public String msg(String key) {
        return color(cfg.getString("mensagens." + key, key));
    }

    public List<String> msgList(String key) {
        List<String> raw = cfg.getStringList("mensagens." + key);
        if (raw == null || raw.isEmpty()) return Collections.singletonList(msg(key));
        List<String> out = new ArrayList<String>(raw.size());
        for (String s : raw) out.add(color(s));
        return out;
    }

    public String prefix() {
        return msg("prefixo");
    }

    static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Set<String> lowerSet(List<String> list) {
        Set<String> set = new HashSet<String>();
        if (list != null) for (String s : list) if (s != null) set.add(s.toLowerCase(Locale.ROOT));
        return Collections.unmodifiableSet(set);
    }
}
