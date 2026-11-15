package com.temporanking;

import com.temporanking.data.PlaytimeManager;
import com.temporanking.data.Ranking;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Commands implements CommandExecutor {

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(me|mes|d|h|m|s)", Pattern.CASE_INSENSITIVE);

    private final TempoRanking plugin;

    Commands(TempoRanking plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("tempo")) return tempo(sender, args);
        if (name.equals("ricos")) return ricos(sender);
        if (name.equals("temporanking")) return admin(sender, label, args);
        return false;
    }

    // ------------------------------------------------------------------ /tempo

    private boolean tempo(CommandSender sender, String[] args) {
        Settings s = plugin.settings();
        if (!sender.hasPermission("temporanking.tempo")) {
            sender.sendMessage(s.prefix() + s.msg("sem-permissao"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            topTempo(sender);
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(s.prefix() + s.msg("so-jogador"));
                return true;
            }
            Player p = (Player) sender;
            send(sender, s.msgList("tempo-proprio"), p.getUniqueId(), p.getName());
            return true;
        }
        if (!sender.hasPermission("temporanking.tempo.outros")) {
            sender.sendMessage(s.prefix() + s.msg("sem-permissao"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        UUID id;
        String targetName;
        if (target != null) {
            id = target.getUniqueId();
            targetName = target.getName();
        } else {
            PlaytimeManager.Record r = plugin.playtime().find(args[0]);
            if (r == null) {
                sender.sendMessage(s.prefix() + s.msg("jogador-nao-encontrado").replace("{jogador}", args[0]));
                return true;
            }
            id = r.uuid;
            targetName = r.name;
        }
        send(sender, s.msgList("tempo-outro"), id, targetName);
        return true;
    }

    private void send(CommandSender to, java.util.List<String> lines, UUID id, String targetName) {
        Settings s = plugin.settings();
        PlaytimeManager pt = plugin.playtime();
        long session = pt.getSession(id);
        String sessionText = session < 0 ? s.msg("offline") : s.time.format(session);
        String total = s.time.format(pt.getTotal(id));
        int pos = plugin.placeholders().timePosition(id);
        String posText = pos < 0 ? s.noPosition : String.valueOf(pos);
        for (String line : lines) {
            to.sendMessage(line.replace("{sessao}", sessionText).replace("{total}", total)
                    .replace("{posicao}", posText).replace("{jogador}", targetName));
        }
    }

    private void topTempo(CommandSender sender) {
        Settings s = plugin.settings();
        Placeholders ph = plugin.placeholders();
        sender.sendMessage(s.msg("top-tempo-cabecalho").replace("{tamanho}", String.valueOf(s.topSize)));
        for (int i = 1; i <= s.topSize; i++) sender.sendMessage(ph.resolve("tempo_top_" + i, null, null));
        if (sender instanceof Player) {
            Player p = (Player) sender;
            int pos = plugin.placeholders().timePosition(p.getUniqueId());
            sender.sendMessage(s.msg("seu-lugar-tempo")
                    .replace("{posicao}", pos < 0 ? s.noPosition : String.valueOf(pos))
                    .replace("{tempo}", s.time.format(plugin.playtime().getTotal(p.getUniqueId()))));
        }
    }

    // ------------------------------------------------------------------ /ricos

    private boolean ricos(CommandSender sender) {
        Settings s = plugin.settings();
        if (!sender.hasPermission("temporanking.ricos")) {
            sender.sendMessage(s.prefix() + s.msg("sem-permissao"));
            return true;
        }
        Placeholders ph = plugin.placeholders();
        sender.sendMessage(s.msg("top-rico-cabecalho").replace("{tamanho}", String.valueOf(s.topSize)));
        for (int i = 1; i <= s.topSize; i++) sender.sendMessage(ph.resolve("rico_top_" + i, null, null));
        if (sender instanceof Player) {
            Player p = (Player) sender;
            int pos = plugin.placeholders().moneyPosition(p.getUniqueId());
            BigDecimal bal = plugin.money().getBalance(p.getUniqueId());
            sender.sendMessage(s.msg("seu-lugar-rico")
                    .replace("{posicao}", pos < 0 ? s.noPosition : String.valueOf(pos))
                    .replace("{saldo}", bal == null ? s.emptyValue : s.money.format(bal)));
        }
        return true;
    }

    // ------------------------------------------------------------ /temporanking

    private boolean admin(final CommandSender sender, String label, String[] args) {
        final Settings s = plugin.settings();
        if (!sender.hasPermission("temporanking.admin")) {
            sender.sendMessage(s.prefix() + s.msg("sem-permissao"));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reloadAll();
            sender.sendMessage(s.prefix() + plugin.settings().msg("recarregado"));
        } else if (sub.equals("atualizar")) {
            plugin.refreshTimeRanking();
            plugin.refreshMoney(new Runnable() {
                @Override public void run() {
                    sender.sendMessage(s.prefix() + s.msg("atualizado"));
                }
            });
        } else if (sub.equals("salvar")) {
            plugin.playtime().save(true);
            sender.sendMessage(s.prefix() + s.msg("salvo"));
        } else if (sub.equals("importar")) {
            plugin.runVanillaImport(sender);
        } else if (sub.equals("settempo") && args.length >= 3) {
            setTempo(sender, args);
        } else if (sub.equals("placeholders")) {
            listPlaceholders(sender);
        } else if (sub.equals("ignorados")) {
            listIgnored(sender);
        } else if (sub.equals("verificar")) {
            sender.sendMessage(s.prefix() + s.msg("verificando"));
            plugin.checkOffline(true, new Runnable() {
                @Override public void run() {
                    sender.sendMessage(s.prefix() + s.msg("atualizado"));
                }
            });
        } else {
            sender.sendMessage(ChatColor.GOLD + "TempoRanking " + plugin.getDescription().getVersion()
                    + ChatColor.GRAY + " (por jogador nos hologramas: "
                    + (plugin.perPlayerActive() ? ChatColor.GREEN + "ativo" : ChatColor.RED + "inativo") + ChatColor.GRAY + ")");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload" + ChatColor.GRAY + " - recarrega a config");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " atualizar" + ChatColor.GRAY + " - recalcula os rankings agora");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " salvar" + ChatColor.GRAY + " - salva dados.yml");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " importar" + ChatColor.GRAY + " - importa tempo das estatisticas vanilla");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " settempo <jogador> <tempo>" + ChatColor.GRAY + " - ex.: 1me 2d 3h 4m 5s");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " placeholders" + ChatColor.GRAY + " - lista os placeholders");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " ignorados" + ChatColor.GRAY + " - quem esta fora dos rankings e por que");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " verificar" + ChatColor.GRAY + " - reconsulta permissao/grupo dos offline agora");
        }
        return true;
    }

    private void setTempo(CommandSender sender, String[] args) {
        Settings s = plugin.settings();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) sb.append(args[i]).append(' ');
        Matcher m = DURATION.matcher(sb.toString());
        long ms = 0;
        boolean any = false;
        long monthMs = 30L * 86400000L;
        while (m.find()) {
            any = true;
            long v = Long.parseLong(m.group(1));
            String u = m.group(2).toLowerCase();
            if (u.startsWith("me")) ms += v * monthMs;
            else if (u.equals("d")) ms += v * 86400000L;
            else if (u.equals("h")) ms += v * 3600000L;
            else if (u.equals("m")) ms += v * 60000L;
            else ms += v * 1000L;
        }
        if (!any) {
            sender.sendMessage(ChatColor.RED + "Tempo invalido. Ex.: 1me 2d 3h 4m 5s");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        UUID id;
        String name;
        if (online != null) {
            id = online.getUniqueId();
            name = online.getName();
        } else {
            PlaytimeManager.Record r = plugin.playtime().find(args[1]);
            if (r == null) {
                sender.sendMessage(s.prefix() + s.msg("jogador-nao-encontrado").replace("{jogador}", args[1]));
                return;
            }
            id = r.uuid;
            name = r.name;
        }
        plugin.playtime().setTotal(id, name, ms);
        plugin.refreshTimeRanking();
        sender.sendMessage(s.prefix() + s.msg("settempo").replace("{jogador}", name).replace("{tempo}", s.time.format(ms)));
    }

    private void listPlaceholders(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Holographic Displays: " + ChatColor.WHITE + "{chave}"
                + ChatColor.GOLD + "  |  PlaceholderAPI: " + ChatColor.WHITE + "%temporanking_chave%");
        sender.sendMessage(ChatColor.YELLOW + "Top (N = 1.." + plugin.settings().topSize + "):");
        sender.sendMessage(ChatColor.GRAY + " tempo_top_N, tempo_top_N_nome, tempo_top_N_tempo");
        sender.sendMessage(ChatColor.GRAY + " rico_top_N, rico_top_N_nome, rico_top_N_saldo");
        sender.sendMessage(ChatColor.YELLOW + "Do proprio jogador:");
        sender.sendMessage(ChatColor.GRAY + " " + String.join(", ", Placeholders.PERSONAL));
        sender.sendMessage(ChatColor.YELLOW + "PAPI dentro dos hologramas: " + ChatColor.GRAY
                + "{papi:identificador} ex.: {papi:vault_eco_balance}");
        if (!plugin.papiKeys().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + " ativos: " + String.join(", ", plugin.papiKeys()));
        }
    }

    private void listIgnored(CommandSender sender) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        for (UUID id : plugin.ignore().ignoredIds()) {
            String name = plugin.playtime().nameOf(id);
            if (name == null) name = Bukkit.getOfflinePlayer(id).getName();
            if (name == null) name = id.toString().substring(0, 8);
            boolean t = plugin.ignore().isIgnoredTime(id);
            boolean mo = plugin.ignore().isIgnoredMoney(id);
            lines.add(ChatColor.WHITE + name + ChatColor.GRAY + " (" + plugin.ignore().reason(id) + ") "
                    + (t ? ChatColor.RED + "tempo " : "") + (mo ? ChatColor.RED + "ricos" : ""));
        }
        java.util.Collections.sort(lines, String.CASE_INSENSITIVE_ORDER);
        sender.sendMessage(ChatColor.GOLD + "Ignorados nos rankings: " + ChatColor.WHITE + lines.size());
        for (String l : lines) sender.sendMessage(" " + l);
    }
}
