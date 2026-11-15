package com.temporanking.hooks;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.temporanking.data.PlaytimeManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** So e carregada se o Essentials estiver instalado. Usada para saber quem esta AFK. */
public final class EssentialsHook implements PlaytimeManager.AfkChecker {

    private final Essentials ess;

    public EssentialsHook(Plugin plugin) {
        this.ess = (Essentials) plugin;
    }

    @Override
    public boolean isAfk(Player p) {
        try {
            User u = ess.getUser(p);
            return u != null && u.isAfk();
        } catch (Throwable t) {
            return false;
        }
    }
}
