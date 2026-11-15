package com.temporanking.util;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formata saldos. DecimalFormat nao e thread-safe, por isso cada thread usa sua copia. */
public final class MoneyFormat {

    private final String symbol;
    private final boolean abbreviate;
    private final List<String> suffixes;
    private final ThreadLocal<DecimalFormat> full;
    private final ThreadLocal<DecimalFormat> shortFmt;

    public MoneyFormat(ConfigurationSection sec) {
        final String pattern = get(sec, "padrao", "#,##0.00");
        final String shortPattern = get(sec, "abreviar-padrao", "#,##0.##");
        String tag = get(sec, "locale", "pt-BR").replace('_', '-');
        String[] p = tag.split("-");
        final Locale locale = p.length >= 2 ? new Locale(p[0], p[1]) : new Locale(p[0]);
        this.symbol = get(sec, "simbolo", "$");
        this.abbreviate = sec != null && sec.getBoolean("abreviar", false);
        List<String> sfx = sec == null ? null : sec.getStringList("abreviar-sufixos");
        if (sfx == null || sfx.isEmpty()) {
            sfx = new ArrayList<String>();
            sfx.add("K"); sfx.add("M"); sfx.add("B"); sfx.add("T"); sfx.add("Q");
        }
        this.suffixes = sfx;
        // valida os padroes agora, para o erro aparecer no carregamento
        new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
        new DecimalFormat(shortPattern, DecimalFormatSymbols.getInstance(locale));
        this.full = new ThreadLocal<DecimalFormat>() {
            @Override protected DecimalFormat initialValue() {
                DecimalFormat f = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
                f.setRoundingMode(RoundingMode.DOWN);
                return f;
            }
        };
        this.shortFmt = new ThreadLocal<DecimalFormat>() {
            @Override protected DecimalFormat initialValue() {
                DecimalFormat f = new DecimalFormat(shortPattern, DecimalFormatSymbols.getInstance(locale));
                f.setRoundingMode(RoundingMode.DOWN);
                return f;
            }
        };
    }

    private static String get(ConfigurationSection sec, String key, String def) {
        return sec == null ? def : sec.getString(key, def);
    }

    public String format(BigDecimal value) {
        if (value == null) value = BigDecimal.ZERO;
        String sign = value.signum() < 0 ? "-" : "";
        BigDecimal abs = value.abs();
        if (abbreviate && abs.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            int idx = -1;
            BigDecimal thousand = BigDecimal.valueOf(1000);
            while (abs.compareTo(thousand) >= 0 && idx < suffixes.size() - 1) {
                abs = abs.divide(thousand);
                idx++;
            }
            return sign + symbol + shortFmt.get().format(abs) + suffixes.get(idx);
        }
        return sign + symbol + full.get().format(abs);
    }
}
