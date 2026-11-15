package com.temporanking.hooks;

import com.temporanking.data.MoneyManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;

/** So e carregada se o Vault estiver instalado. */
public final class VaultHook implements MoneyManager.BalanceSource {

    private final Economy economy;

    private VaultHook(Economy economy) {
        this.economy = economy;
    }

    public static VaultHook create() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) return null;
        return new VaultHook(rsp.getProvider());
    }

    public String getName() {
        return economy.getName();
    }

    @Override
    public BigDecimal getBalance(OfflinePlayer player) {
        double d;
        if (player.getName() != null) {
            d = economy.getBalance(player.getName());
        } else {
            d = economy.getBalance(player);
        }
        return BigDecimal.valueOf(d);
    }
}
