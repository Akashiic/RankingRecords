package com.temporanking.data;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Leitor leve dos arquivos plugins/Essentials/userdata/UUID.yml. Le apenas as chaves de
 * primeiro nivel que interessam (lastAccountName, money, npc), sem carregar o YAML inteiro.
 * So faz I/O, pode rodar em thread assincrona.
 */
public final class EssentialsUserdata {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Account {
        public final UUID uuid;
        public final String name;
        public final BigDecimal money;
        public final boolean npc;

        Account(UUID uuid, String name, BigDecimal money, boolean npc) {
            this.uuid = uuid;
            this.name = name;
            this.money = money;
            this.npc = npc;
        }
    }

    private EssentialsUserdata() {
    }

    /**
     * @param startingBalance saldo usado quando o arquivo nao tem "money" (o Essentials
     *                        so grava o saldo depois que ele muda)
     */
    public static List<Account> readAll(File userdataDir, BigDecimal startingBalance) {
        List<Account> out = new ArrayList<Account>();
        File[] files = userdataDir == null ? null : userdataDir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".yml")) continue;
            UUID id = VanillaImporter.parseUuid(n.substring(0, n.length() - 4));
            if (id == null) continue;
            String name = null;
            BigDecimal money = null;
            boolean npc = false;
            try {
                for (String line : Files.readAllLines(f.toPath(), UTF8)) {
                    if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '#') continue;
                    if (line.startsWith("lastAccountName:")) {
                        name = unquote(line.substring(16));
                    } else if (line.startsWith("money:")) {
                        try {
                            money = new BigDecimal(unquote(line.substring(6)));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (line.startsWith("npc:")) {
                        npc = "true".equalsIgnoreCase(unquote(line.substring(4)));
                    }
                }
            } catch (IOException ignored) {
                continue;
            } catch (RuntimeException ignored) { // arquivo com encoding estranho etc.
                continue;
            }
            if (name == null || name.isEmpty()) continue;
            out.add(new Account(id, name, money != null ? money : startingBalance, npc));
        }
        return out;
    }

    static String unquote(String v) {
        v = v.trim();
        if (v.length() >= 2 && ((v.startsWith("'") && v.endsWith("'")) || (v.startsWith("\"") && v.endsWith("\"")))) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }
}
