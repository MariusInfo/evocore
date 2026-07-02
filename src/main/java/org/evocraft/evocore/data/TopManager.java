package org.evocraft.evocore.data;

import java.util.*;
import java.util.stream.Collectors;

public class TopManager {

    public static List<Map.Entry<String, Double>> getTopPage(String category, int page) {
        Map<String, Double> rawData;

        if (category.equals("bani")) {
            rawData = new HashMap<>();
            Map<UUID, Double> balances = EconomyManager.get().getAllAccounts();
            if (balances != null) {
                balances.forEach((uuid, bal) -> {
                    String name = PlayerStatsManager.get().getNameByUUID(uuid);
                    if (name != null && !name.equals("Necunoscut") && bal > 0) {
                        rawData.put(name, bal);
                    }
                });
            }
        }
        else if (category.equals("kills") || category.equals("decese") || category.equals("ore") || category.equals("claimuri")) {
            rawData = PlayerStatsManager.get().getTopData(category);
        } else {
            rawData = new HashMap<>();
        }

        return rawData.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .skip((page - 1) * 7L)
                .limit(7)
                .collect(Collectors.toList());
    }
}