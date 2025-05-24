package sirou95.tokens;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public class TokensPlugin extends JavaPlugin {

    private double tokenRate = 0.0;
    private Economy economy;
    private File balanceFile;
    private FileConfiguration balances;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault ou un plugin d'économie est manquant !");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadBalances();
        updateTokenRate();
        startRateUpdater();
        getLogger().info("TokensPlugin activé !");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        var rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadBalances() {
        balanceFile = new File(getDataFolder(), "balances.yml");
        if (!balanceFile.exists()) {
            saveResource("balances.yml", false);
        }
        balances = YamlConfiguration.loadConfiguration(balanceFile);
    }

    private void saveBalances() {
        try {
            balances.save(balanceFile);
        } catch (Exception e) {
            getLogger().warning("Erreur lors de la sauvegarde des soldes tokens.");
        }
    }

    private void startRateUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateTokenRate();
            }
        }.runTaskTimerAsynchronously(this, 0L, 30 * 60 * 20L); // toutes les 30 minutes
    }

    private void updateTokenRate() {
        // Taux aléatoire entre 500 et 3000
        tokenRate = 500 + Math.random() * (3000 - 500);
        getLogger().info("Nouveau taux de Tokens : $" + String.format("%.2f", tokenRate));
    }

    private double getBalance(UUID uuid) {
        return balances.getDouble(uuid.toString(), 0.0);
    }

    private void setBalance(UUID uuid, double amount) {
        balances.set(uuid.toString(), amount);
        saveBalances();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§9[§eCrypto&9] §cCette commande est réservée aux joueurs.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        String prefix = "§9[§eCrypto&9] §r";

        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            double balance = getBalance(uuid);
            player.sendMessage(prefix + "Votre solde Tokens virtuel : §a" + String.format("%.4f", balance) + " TOKENS");
            player.sendMessage(prefix + "Taux actuel : §a$" + String.format("%.2f", tokenRate) + " USD par TOKEN");
            return true;
        }

        if (args[0].equalsIgnoreCase("buy")) {
            if (args.length != 2) {
                player.sendMessage(prefix + "§cUtilisation correcte : /tokens buy <montant en USD>");
                return true;
            }
            try {
                double usd = Double.parseDouble(args[1]);
                if (usd <= 0) {
                    player.sendMessage(prefix + "§cVeuillez entrer un montant positif.");
                    return true;
                }
                double tokensToBuy = usd / tokenRate;
                if (economy.getBalance(player) < usd) {
                    player.sendMessage(prefix + "§cVous n'avez pas assez d'argent.");
                    return true;
                }
                economy.withdrawPlayer(player, usd);
                setBalance(uuid, getBalance(uuid) + tokensToBuy);
                player.sendMessage(prefix + "§aAchat réussi : §b" + String.format("%.4f", tokensToBuy) + " TOKENS pour §e$" + String.format("%.2f", usd));
            } catch (NumberFormatException e) {
                player.sendMessage(prefix + "§cMontant invalide.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length != 2) {
                player.sendMessage(prefix + "§cUtilisation correcte : /tokens sell <montant en TOKENS>");
                return true;
            }
            try {
                double tokensToSell = Double.parseDouble(args[1]);
                if (tokensToSell <= 0) {
                    player.sendMessage(prefix + "§cVeuillez entrer un montant positif.");
                    return true;
                }
                double currentBalance = getBalance(uuid);
                if (currentBalance < tokensToSell) {
                    player.sendMessage(prefix + "§cVous n'avez pas assez de TOKENS.");
                    return true;
                }
                double usdGain = tokensToSell * tokenRate;
                setBalance(uuid, currentBalance - tokensToSell);
                economy.depositPlayer(player, usdGain);
                player.sendMessage(prefix + "§aVente réussie : §b" + String.format("%.4f", tokensToSell) + " TOKENS pour §e$" + String.format("%.2f", usdGain));
            } catch (NumberFormatException e) {
                player.sendMessage(prefix + "§cMontant invalide.");
            }
            return true;
        }

        return false;
    }

}
