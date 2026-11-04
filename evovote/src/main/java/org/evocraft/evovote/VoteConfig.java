package org.evocraft.evovote;

import net.minecraftforge.common.ForgeConfigSpec;

public class VoteConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> VOTE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> VOTE_KEY_NAME;
    public static final ForgeConfigSpec.IntValue VOTE_KEY_AMOUNT;
    public static final ForgeConfigSpec.BooleanValue BROADCAST_VOTES;

    static {
        BUILDER.push("Vote Settings");
        VOTE_URL = BUILDER.comment("The public URL where players can vote for your server.")
                .define("voteUrl", "https://evocraft.ro/vote.php");
        VOTE_KEY_NAME = BUILDER.comment("The crate key identifier granted upon voting.")
                .define("voteKeyName", "vote");
        VOTE_KEY_AMOUNT = BUILDER.comment("Amount of vote crate keys to reward per vote.")
                .defineInRange("voteKeyAmount", 1, 1, 100);
        BROADCAST_VOTES = BUILDER.comment("Whether to broadcast a global message and GUI banner when a player votes.")
                .define("broadcastVotes", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
