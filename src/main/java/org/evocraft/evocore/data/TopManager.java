package org.evocraft.evocore.data;

import java.util.*;
import java.util.stream.Collectors;

public class TopManager {
    private static final int TOP_ENTRIES_PER_PAGE = 12;

    public static List<Map.Entry<String, Double>> getTopPage(String category, int page) {
        int safePage = Math.max(1, page);
        Map<String, Double> rawData;

        if (category.equals("bani")) {
            rawData = new HashMap<>();
            Map<UUID, Double> balances = EconomyManager.get().getAllAccounts();
            if (balances != null) {
                balances.forEach((uuid, bal) -> {
                    String name = PlayerStatsManager.get().getNameByUUID(uuid);
                    if (name != null && !name.equals("Necunoscut") && !name.equals("Unknown") && bal > 0) {
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
                .skip((long) (safePage - 1) * TOP_ENTRIES_PER_PAGE)
                .limit(TOP_ENTRIES_PER_PAGE)
                .collect(Collectors.toList());
    }
}
