package com.temporanking.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le o tempo jogado das estatisticas vanilla do 1.7.10 (world/stats/UUID.json, chave
 * "stat.playOneMinute", em ticks) e os nomes do usercache.json e do userdata do Essentials.
 * So faz I/O de arquivos, pode rodar em thread assincrona.
 */
public final class VanillaImporter {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Pattern PLAY_TICKS = Pattern.compile("\"stat\\.playOneMinute\"\\s*:\\s*(\\d+)");
    private static final Pattern CACHE_ENTRY = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern CACHE_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CACHE_UUID = Pattern.compile("\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

    public final Map<UUID, Long> ticks = new HashMap<UUID, Long>();
    public final Map<UUID, String> names = new HashMap<UUID, String>();
    public int filesRead;

    public void readStats(File statsDir) {
        File[] files = statsDir == null ? null : statsDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".json")) continue;
            UUID id = parseUuid(n.substring(0, n.length() - 5));
            if (id == null) continue;
            try {
                String json = new String(Files.readAllBytes(f.toPath()), UTF8);
                Matcher m = PLAY_TICKS.matcher(json);
                if (m.find()) {
                    ticks.put(id, Long.parseLong(m.group(1)));
                    filesRead++;
                }
            } catch (IOException | NumberFormatException ignored) {
            }
        }
    }

    public void readUserCache(File usercache) {
        if (usercache == null || !usercache.isFile()) return;
        try {
            String json = new String(Files.readAllBytes(usercache.toPath()), UTF8);
            Matcher m = CACHE_ENTRY.matcher(json);
            while (m.find()) {
                String obj = m.group();
                Matcher mn = CACHE_NAME.matcher(obj);
                Matcher mu = CACHE_UUID.matcher(obj);
                if (mn.find() && mu.find()) {
                    UUID id = parseUuid(mu.group(1));
                    if (id != null && !names.containsKey(id)) names.put(id, mn.group(1));
                }
            }
        } catch (IOException ignored) {
        }
    }

    /** Usa o lastAccountName do Essentials para quem nao estava no usercache. */
    public void readEssentialsNames(File userdataDir) {
        File[] files = userdataDir == null ? null : userdataDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".yml")) continue;
            UUID id = parseUuid(n.substring(0, n.length() - 4));
            if (id == null || names.containsKey(id) || !ticks.containsKey(id)) continue;
            try {
                List<String> lines = Files.readAllLines(f.toPath(), UTF8);
                for (String line : lines) {
                    if (line.startsWith("lastAccountName:")) {
                        String v = EssentialsUserdata.unquote(line.substring("lastAccountName:".length()));
                        if (v.length() > 0) names.put(id, v);
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    static UUID parseUuid(String s) {
        try {
            return s.length() == 36 ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
