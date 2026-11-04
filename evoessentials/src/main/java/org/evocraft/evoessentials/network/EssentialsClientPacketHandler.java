package org.evocraft.evoessentials.network;

import java.util.List;
import org.evocraft.evoessentials.client.ClientHomeData;

public class EssentialsClientPacketHandler {
    public static void handleSyncHomes(int currentCount, int maxCount, List<String> homes) {
        ClientHomeData.currentHomesCount = currentCount;
        ClientHomeData.maxHomesCount = maxCount;
        ClientHomeData.playerHomes = homes;

        if (net.minecraft.client.Minecraft.getInstance().screen != null) {
            net.minecraft.client.Minecraft.getInstance().screen.init(
                    net.minecraft.client.Minecraft.getInstance(),
                    net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight()
            );
        }
    }
}
