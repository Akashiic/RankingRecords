package com.temporanking.api;

import com.temporanking.TempoRanking;
import com.temporanking.data.Ranking;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * API publica do TempoRanking, feita para usar no Skript com o skript-reflect
 * (e em outros plugins Java). Todos os metodos sao estaticos.
 *
 * <pre>
 * import:
 *     com.temporanking.api.TempoRankingAPI
 *
 * command /meutempo:
 *     trigger:
 *         send "Total: %TempoRankingAPI.getFormattedTotal(player)%"
 *         send "Posicao: %TempoRankingAPI.getTimePosition(player)%"
 * </pre>
 *
 * Tempos em milissegundos ou segundos (long). Posicoes comecam em 1; -1 = sem dados.
 * Os metodos de leitura podem ser chamados de qualquer thread; os que alteram dados
 * (set/add/refresh) devem ser chamados na thread principal (o normal no Skript).
 */
public final class TempoRankingAPI {

    private TempoRankingAPI() {
    }

    private static TempoRanking plugin() {
        TempoRanking p = TempoRanking.getInstance();
        if (p == null) throw new IllegalStateException("TempoRanking nao esta ativo");
        return p;
    }

    // --------------------------------------------------------------- tempo

    /** Tempo total jogado, em milissegundos (inclui a sessao atual). */
    public static long getTotalMillis(OfflinePlayer player) {
        return plugin().playtime().getTotal(player.getUniqueId());
    }

    /** Tempo total jogado, em segundos. */
    public static long getTotalSeconds(OfflinePlayer player) {
        return getTotalMillis(player) / 1000L;
    }

    /** Tempo total formatado como na config, ex.: "2 meses 3d 4h 5m 6s". */
    public static String getFormattedTotal(OfflinePlayer player) {
        return formatTime(getTotalMillis(player));
    }

    /** Duracao da sessao atual em milissegundos; -1 se o jogador esta offline. */
    public static long getSessionMillis(OfflinePlayer player) {
        return plugin().playtime().getSession(player.getUniqueId());
    }

    public static long getSessionSeconds(OfflinePlayer player) {
        long ms = getSessionMillis(player);
        return ms < 0 ? -1L : ms / 1000L;
    }

    /** Sessao atual formatada; "0s" se offline. */
    public static String getFormattedSession(OfflinePlayer player) {
        return formatTime(Math.max(0L, getSessionMillis(player)));
    }

    /**
     * Posicao no ranking de tempo. Para quem e ignorado (OP, permissao, grupo) devolve a
     * posicao que teria. -1 se nao tem tempo contado.
     */
    public static int getTimePosition(OfflinePlayer player) {
        return plugin().placeholders().timePosition(player.getUniqueId());
    }

    /** Formata milissegundos no formato da config (meses, dias, horas, minutos, segundos). */
    public static String formatTime(long millis) {
        return plugin().settings().time.format(millis);
    }

    /** Formata segundos no formato da config. */
    public static String formatSeconds(long seconds) {
        return formatTime(seconds * 1000L);
    }

    /** Define o tempo total do jogador (milissegundos). Thread principal. */
    public static void setTotalMillis(OfflinePlayer player, long millis) {
        TempoRanking p = plugin();
        p.playtime().setTotal(player.getUniqueId(), player.getName(), millis);
        p.refreshTimeRanking();
    }

    /** Soma (ou subtrai, se negativo) tempo ao total do jogador (milissegundos). Thread principal. */
    public static void addTotalMillis(OfflinePlayer player, long millis) {
        setTotalMillis(player, Math.max(0L, plugin().playtime().getTotal(player.getUniqueId()) + millis));
    }

    // ---------------------------------------------------------- top tempo

    /** Nome de quem esta na posicao do top de tempo (1 = primeiro), ou null. */
    public static String getTopTimeName(int position) {
        Ranking.Entry<Long> e = plugin().playtime().getRanking().get(position);
        return e == null ? null : e.name;
    }

    /** UUID (texto) de quem esta na posicao do top de tempo, ou null. */
    public static String getTopTimeUuid(int position) {
        Ranking.Entry<Long> e = plugin().playtime().getRanking().get(position);
        return e == null ? null : e.uuid.toString();
    }

    /** Tempo total (ms) de quem esta na posicao do top de tempo; -1 se vazia. Ao vivo se online. */
    public static long getTopTimeMillis(int position) {
        TempoRanking p = plugin();
        Ranking.Entry<Long> e = p.playtime().getRanking().get(position);
        if (e == null) return -1L;
        return p.playtime().isOnline(e.uuid) ? p.playtime().getTotal(e.uuid) : e.value;
    }

    /** Tempo formatado de quem esta na posicao do top de tempo, ou null. */
    public static String getTopTimeFormatted(int position) {
        long ms = getTopTimeMillis(position);
        return ms < 0 ? null : formatTime(ms);
    }

    /** Quantos jogadores estao no ranking de tempo (sem os ignorados). */
    public static int getTimeRankingSize() {
        return plugin().playtime().getRanking().size();
    }

    // -------------------------------------------------------------- ricos

    /** Saldo do jogador (ao vivo se online; ultima leitura se offline). -1 se desconhecido. */
    public static double getBalance(OfflinePlayer player) {
        BigDecimal b = plugin().money().getBalance(player.getUniqueId());
        return b == null ? -1D : b.doubleValue();
    }

    /** Saldo formatado como na config, ex.: "$150.781.938,12"; null se desconhecido. */
    public static String getFormattedBalance(OfflinePlayer player) {
        BigDecimal b = plugin().money().getBalance(player.getUniqueId());
        return b == null ? null : plugin().settings().money.format(b);
    }

    /** Posicao no ranking de ricos (para ignorados, a posicao que teria). -1 sem dados. */
    public static int getMoneyPosition(OfflinePlayer player) {
        return plugin().placeholders().moneyPosition(player.getUniqueId());
    }

    /** Formata um valor no formato de dinheiro da config. */
    public static String formatMoney(double value) {
        return plugin().settings().money.format(BigDecimal.valueOf(value));
    }

    public static String getTopMoneyName(int position) {
        Ranking.Entry<BigDecimal> e = plugin().money().getRanking().get(position);
        return e == null ? null : e.name;
    }

    public static String getTopMoneyUuid(int position) {
        Ranking.Entry<BigDecimal> e = plugin().money().getRanking().get(position);
        return e == null ? null : e.uuid.toString();
    }

    /** Saldo de quem esta na posicao do top de ricos; -1 se vazia. */
    public static double getTopMoneyBalance(int position) {
        Ranking.Entry<BigDecimal> e = plugin().money().getRanking().get(position);
        return e == null ? -1D : e.value.doubleValue();
    }

    public static String getTopMoneyFormatted(int position) {
        Ranking.Entry<BigDecimal> e = plugin().money().getRanking().get(position);
        return e == null ? null : plugin().settings().money.format(e.value);
    }

    public static int getMoneyRankingSize() {
        return plugin().money().getRanking().size();
    }

    // ------------------------------------------------------------- outros

    public static boolean isIgnoredTime(OfflinePlayer player) {
        return plugin().ignore().isIgnoredTime(player.getUniqueId());
    }

    public static boolean isIgnoredMoney(OfflinePlayer player) {
        return plugin().ignore().isIgnoredMoney(player.getUniqueId());
    }

    /** Tamanho do top configurado (tamanho-top). */
    public static int getTopSize() {
        return plugin().settings().topSize;
    }

    /**
     * Resolve qualquer placeholder do plugin (a mesma chave do HD, sem as chaves).
     * Ex.: TempoRankingAPI.placeholder("tempo_top_1", player). null se a chave nao existe.
     */
    public static String placeholder(String key, OfflinePlayer viewer) {
        return plugin().placeholders().resolve(key,
                viewer == null ? null : viewer.getUniqueId(),
                viewer == null ? null : viewer.getName());
    }

    /** Recalcula os dois rankings agora (o de ricos termina em segundo plano). Thread principal. */
    public static void refresh() {
        TempoRanking p = plugin();
        p.refreshTimeRanking();
        p.refreshMoneyRanking();
    }

    /** UUID de texto para OfflinePlayer (util com getTopTimeUuid/getTopMoneyUuid). */
    public static OfflinePlayer toPlayer(String uuid) {
        return org.bukkit.Bukkit.getOfflinePlayer(UUID.fromString(uuid));
    }
}
