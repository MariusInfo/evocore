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
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void generateDefaults() {
        messages.put("money_balance", "§aPortofel: §e%s Lei");
        messages.put("money_set", "§aSetat %s Lei pentru %s");
        messages.put("money_pay_sent", "§aAi trimis §e%s Lei");
        messages.put("money_pay_received", "§aAi primit §e%s Lei");
        messages.put("money_insufficient", "§cFonduri insuficiente!");

        messages.put("job_join_success", "§aTe-ai angajat cu succes ca: §e%s");
        messages.put("job_leave_success", "§cAi demisionat din funcția de %s");
        messages.put("job_limit_reached", "§cAi atins limita de 3 joburi!");
        messages.put("job_already_joined", "§eEști deja angajat! Folosește butonul ROȘU.");

        messages.put("ah_sell_success", "§aVândut pe AH cu %s Lei");
        messages.put("ah_buy_success", "§aAi cumpărat itemul cu succes!");
        messages.put("ah_return_success", "§aȚi-ai recuperat itemul!");

        messages.put("action_bar_gain", "§a+ %s Lei §7| §b+ %d XP §7(%s)");
        messages.put("level_up", "§6⬆ LEVEL UP! §e%s §fa atins nivelul §b%d");

        save();
    }

    private void save() {
        try (Writer writer = new FileWriter(saveFile)) {
            gson.toJson(messages, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }
}