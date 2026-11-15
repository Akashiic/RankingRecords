package com.temporanking.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Formata milissegundos em meses, dias, horas, minutos e segundos. Imutavel e thread-safe. */
public final class TimeFormat {

    private static final String[] KEYS = {"meses", "dias", "horas", "minutos", "segundos"};
    private static final String[][] DEFAULTS = {
            {" mes", " meses"}, {"d", "d"}, {"h", "h"}, {"m", "m"}, {"s", "s"}};

    private final long secondsPerMonth;
    private final boolean hideZeros;
    private final int maxUnits;
    private final String separator;
    private final String lastSeparator;
    private final String[] singular = new String[5];
    private final String[] plural = new String[5];

    public TimeFormat(ConfigurationSection sec) {
        int daysPerMonth = Math.max(1, sec == null ? 30 : sec.getInt("dias-por-mes", 30));
        this.secondsPerMonth = daysPerMonth * 86400L;
        this.hideZeros = sec == null || sec.getBoolean("esconder-zeros", true);
        this.maxUnits = sec == null ? 0 : Math.max(0, sec.getInt("maximo-unidades", 0));
        this.separator = sec == null ? " " : sec.getString("separador", " ");
        this.lastSeparator = sec == null ? " " : sec.getString("separador-final", this.separator);
        for (int i = 0; i < 5; i++) {
            ConfigurationSection u = sec == null ? null : sec.getConfigurationSection("unidades." + KEYS[i]);
            singular[i] = u == null ? DEFAULTS[i][0] : u.getString("singular", DEFAULTS[i][0]);
            plural[i] = u == null ? DEFAULTS[i][1] : u.getString("plural", DEFAULTS[i][1]);
        }
    }

    public String format(long millis) {
        long s = Math.max(0L, millis / 1000L);
        long[] v = new long[5];
        v[0] = s / secondsPerMonth;
        s %= secondsPerMonth;
        v[1] = s / 86400L;
        s %= 86400L;
        v[2] = s / 3600L;
        s %= 3600L;
        v[3] = s / 60L;
        v[4] = s % 60L;

        int first = -1;
        for (int i = 0; i < 5; i++) {
            if (v[i] != 0) {
                first = i;
                break;
            }
        }
        if (first < 0) return "0" + plural[4];
        // maximo-unidades conta a partir da maior unidade nao-zero
        int last = maxUnits > 0 ? Math.min(4, first + maxUnits - 1) : 4;

        List<String> parts = new ArrayList<String>(5);
        for (int i = first; i <= last; i++) {
            if (hideZeros && v[i] == 0) continue;
            parts.add(v[i] + (v[i] == 1 ? singular[i] : plural[i]));
        }
        if (parts.isEmpty()) return "0" + plural[4];

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i == parts.size() - 1 ? lastSeparator : separator);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
