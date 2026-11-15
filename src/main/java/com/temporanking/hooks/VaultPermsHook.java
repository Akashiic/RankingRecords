package com.temporanking.hooks;

import com.temporanking.data.IgnoreManager;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/** Permissoes/grupos via Vault (LuckPerms). So e carregada se o Vault estiver instalado. */
public final class VaultPermsHook implements IgnoreManager.OfflinePerms {

    private final Permission perms;

    private VaultPermsHook(Permission perms) {
        this.perms = perms;
    }

    public static VaultPermsHook create() {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp == null || rsp.getProvider() == null) return null;
        return new VaultPermsHook(rsp.getProvider());
    }

    public String getName() {
        return perms.getName();
    }

    /** Fora da thread principal para jogadores offline (o LuckPerms carrega o usuario do banco). */
    @Override
    public boolean has(OfflinePlayer p, String permission) {
        return perms.playerHas((String) null, p, permission);
    }

    @Override
    public Collection<String> groups(OfflinePlayer p) {
        if (!perms.hasGroupSupport()) return Collections.emptyList();
        String[] g = perms.getPlayerGroups((String) null, p);
        return g == null ? Collections.<String>emptyList() : Arrays.asList(g);
    }

    @Override
    public Collection<String> groups(Player p) {
        if (!perms.hasGroupSupport()) return Collections.emptyList();
        String[] g = perms.getPlayerGroups(p);
        return g == null ? Collections.<String>emptyList() : Arrays.asList(g);
    }
}
