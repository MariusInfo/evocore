package org.evocraft.evocore.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class TranslationManager {
    private static TranslationManager INSTANCE;
    private final Map<String, String> messages = new HashMap<>();
    private final File saveFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public TranslationManager() {
        this.saveFile = FMLPaths.CONFIGDIR.get().resolve("evo_economy/messages.json").toFile();
        if (!saveFile.getParentFile().exists()) saveFile.getParentFile().mkdirs();
        load();
    }

    public static void initialize() {
        if (INSTANCE == null) INSTANCE = new TranslationManager();
    }

    public static String get(String key) {
        return INSTANCE.messages.getOrDefault(key, key);
    }

    public static String get(String key, Object... args) {
        String msg = INSTANCE.messages.getOrDefault(key, key);
        try {
            return String.format(msg, args);
        } catch (Exception e) {
            return msg;
        }
    }

    private void load() {
        if (!saveFile.exists()) {
            generateDefaults();
            return;
        }
        try (Reader reader = new FileReader(saveFile)) {
            Map<String, String> loaded = gson.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            if (loaded != null) messages.putAll(loaded);
            boolean changed = false;
            for (Map.Entry<String, String> entry : defaults().entrySet()) {
                String current = messages.get(entry.getKey());
                if (current == null || shouldRefreshValue(entry.getKey(), current)) {
                    messages.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (changed) save();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void generateDefaults() {
        messages.putAll(defaults());
        save();
    }

    private Map<String, String> defaults() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("money_balance", "§aWallet: §e%s Evo");
        defaults.put("money_set", "§aSet %s Evo for %s");
        defaults.put("money_pay_sent", "§aYou sent §e%s Evo");
        defaults.put("money_pay_received", "§aYou received §e%s Evo");
        defaults.put("money_insufficient", "§cInsufficient funds!");

        defaults.put("job_join_success", "§aYou joined successfully as: §e%s");
        defaults.put("job_leave_success", "§cYou left the %s job");
        defaults.put("job_limit_reached", "§cYou reached your job limit!");
        defaults.put("job_already_joined", "§eYou already have this job! Use the red button.");

        defaults.put("ah_sell_success", "§aListed on AH for %s Evo");
        defaults.put("ah_buy_success", "§aYou bought the item successfully!");
        defaults.put("ah_return_success", "§aYou recovered your item!");

        defaults.put("action_bar_gain", "§a+ %s Evo §7| §b+ %d XP §7(%s)");
        defaults.put("level_up", "§6LEVEL UP! §e%s §freached level §b%d");
        return defaults;
    }

    private boolean shouldRefreshValue(String key, String value) {
        if (value == null) return true;
        if (value.contains("Lei")) return true;
        if (value.contains("Ai ") || value.contains("Te-ai") || value.contains("Vândut") || value.contains("cumpărat")) return true;
        return key.startsWith("money_") && value.contains("Portofel");
    }

    private void save() {
        try (Writer writer = new FileWriter(saveFile)) {
            gson.toJson(messages, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }
}
