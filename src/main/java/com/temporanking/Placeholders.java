package com.temporanking;

import com.temporanking.data.Ranking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolve os placeholders (sem chaves/porcentagem). Usado pelo Holographic Displays,
 * pelo listener de pacotes e pelo PlaceholderAPI. Pode ser chamado de qualquer thread.
 *
 * Globais (N = 1..tamanho-top):
 *   tempo_top_N, tempo_top_N_nome, tempo_top_N_tempo
 *   rico_top_N,  rico_top_N_nome,  rico_top_N_saldo
 * Do proprio jogador:
 *   tempo_meu_total, tempo_minha_sessao, tempo_minha_posicao,
 *   rico_meu_saldo, rico_minha_posicao, meu_nome, tempo_ignorado, rico_ignorado
 * PAPI dentro dos hologramas:
 *   papi:identificador_parametro  (no HD: {papi:vault_eco_balance})
 */
public final class Placeholders {

    public static final List<String> PERSONAL = Collections.unmodifiableList(Arrays.asList(
            "tempo_meu_total", "tempo_minha_sessao", "tempo_minha_posicao",
            "rico_meu_saldo", "rico_minha_posicao", "meu_nome",
            "tempo_ignorado", "rico_ignorado"));
    private static final Set<String> PERSONAL_SET = new HashSet<String>(PERSONAL);

    private final TempoRanking plugin;

    Placeholders(TempoRanking plugin) {
        this.plugin = plugin;
    }

    public static final String PAPI_PREFIX = "papi:";

    public static boolean isPersonal(String key) {
        return PERSONAL_SET.contains(key) || key.startsWith(PAPI_PREFIX);
    }

    /** Todas as chaves globais registradas, para o tamanho de top atual. */
    public List<String> globalKeys() {
        int size = plugin.settings().topSize;
        List<String> keys = new ArrayList<String>(size * 6);
        for (int i = 1; i <= size; i++) {
            keys.add("tempo_top_" + i);
            keys.add("tempo_top_" + i + "_nome");
            keys.add("tempo_top_" + i + "_tempo");
            keys.add("rico_top_" + i);
            keys.add("rico_top_" + i + "_nome");
            keys.add("rico_top_" + i + "_saldo");
        }
        return keys;
    }

    /**
     * @param viewer jogador que esta vendo (null para placeholders globais)
     * @return texto ou null se a chave nao existe
     */
    public String resolve(String key, UUID viewer, String viewerName) {
        if (key.startsWith("tempo_top_")) return top(true, key.substring(10));
        if (key.startsWith("rico_top_")) return top(false, key.substring(9));
        if (key.startsWith(PAPI_PREFIX)) {
            if (viewer == null) return "";
            return plugin.papiValue(viewer, key.substring(PAPI_PREFIX.length()));
        }
        if (!PERSONAL_SET.contains(key)) return null;
        if (viewer == null) return "";
        Settings s = plugin.settings();
        if ("tempo_meu_total".equals(key)) {
            return s.time.format(plugin.playtime().getTotal(viewer));
        }
        if ("tempo_minha_sessao".equals(key)) {
            return s.time.format(Math.max(0L, plugin.playtime().getSession(viewer)));
        }
        if ("tempo_minha_posicao".equals(key)) {
            int p = timePosition(viewer);
            return p < 0 ? s.noPosition : String.valueOf(p);
        }
        if ("rico_meu_saldo".equals(key)) {
            BigDecimal b = plugin.money().getBalance(viewer);
            return b == null ? s.emptyValue : s.money.format(b);
        }
        if ("rico_minha_posicao".equals(key)) {
            int p = moneyPosition(viewer);
            return p < 0 ? s.noPosition : String.valueOf(p);
        }
        if ("tempo_ignorado".equals(key)) {
            return plugin.ignore().isIgnoredTime(viewer) ? s.ignoredYes : s.ignoredNo;
        }
        if ("rico_ignorado".equals(key)) {
            return plugin.ignore().isIgnoredMoney(viewer) ? s.ignoredYes : s.ignoredNo;
        }
        if ("meu_nome".equals(key)) {
            return viewerName == null ? "" : viewerName;
        }
        return null;
    }

    private static final java.util.Comparator<Long> LONG_CMP = new java.util.Comparator<Long>() {
        @Override public int compare(Long a, Long b) { return a.compareTo(b); }
    };
    private static final java.util.Comparator<BigDecimal> BIG_CMP = new java.util.Comparator<BigDecimal>() {
        @Override public int compare(BigDecimal a, BigDecimal b) { return a.compareTo(b); }
    };

    /** Posicao no ranking de tempo; para quem esta fora (ex.: OP), a posicao que teria. -1 sem dados. */
    public int timePosition(UUID id) {
        long total = plugin.playtime().getTotal(id);
        return plugin.playtime().getRanking().positionFor(id, total > 0 ? total : null, LONG_CMP);
    }

    /** Posicao no ranking de ricos; para quem esta fora, a posicao que teria. -1 sem dados. */
    public int moneyPosition(UUID id) {
        return plugin.money().getRanking().positionFor(id, plugin.money().getBalance(id), BIG_CMP);
    }

    /** rest = "N", "N_nome", "N_tempo"/"N_saldo" */
    private String top(boolean time, String rest) {
        String field = null;
        int us = rest.indexOf('_');
        String num = rest;
        if (us >= 0) {
            num = rest.substring(0, us);
            field = rest.substring(us + 1);
        }
        int pos;
        try {
            pos = Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return null;
        }
        if (pos < 1) return null;
        if (field != null && !field.equals("nome") && !field.equals(time ? "tempo" : "saldo")) return null;

        Settings s = plugin.settings();
        String value;
        String name;
        if (time) {
            Ranking.Entry<Long> e = plugin.playtime().getRanking().get(pos);
            if (e == null) return empty(s, field, pos, true);
            name = e.name;
            // usa o valor ao vivo para quem esta online, assim o top tambem "anda" a cada segundo
            long ms = plugin.playtime().isOnline(e.uuid) ? plugin.playtime().getTotal(e.uuid) : e.value;
            value = s.time.format(ms);
        } else {
            Ranking.Entry<BigDecimal> e = plugin.money().getRanking().get(pos);
            if (e == null) return empty(s, field, pos, false);
            name = e.name;
            value = s.money.format(e.value);
        }
        if (field == null) {
            String line = time ? s.lineTime : s.lineMoney;
            return line.replace("{posicao}", String.valueOf(pos)).replace("{nome}", name)
                    .replace("{tempo}", value).replace("{saldo}", value);
        }
        return field.equals("nome") ? name : value;
    }

    private static String empty(Settings s, String field, int pos, boolean time) {
        if (field == null) return (time ? s.emptyLineTime : s.emptyLine).replace("{posicao}", String.valueOf(pos));
        return field.equals("nome") ? s.emptyName : s.emptyValue;
    }
}
