package org.evocraft.evocore.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class TopNPC extends PathfinderMob {

    public static final EntityDataAccessor<String> CATEGORY = SynchedEntityData.defineId(TopNPC.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(TopNPC.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<String> PLAYER_NAME = SynchedEntityData.defineId(TopNPC.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> DISPLAY_VALUE = SynchedEntityData.defineId(TopNPC.class, EntityDataSerializers.STRING);
    // NOU: UUID-ul real al jucătorului pentru Quick Skin
    public static final EntityDataAccessor<String> PLAYER_UUID_STR = SynchedEntityData.defineId(TopNPC.class, EntityDataSerializers.STRING);

    public TopNPC(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CATEGORY, "bani");
        this.entityData.define(RANK, 1);
        this.entityData.define(PLAYER_NAME, "Nimeni");
        this.entityData.define(DISPLAY_VALUE, "0");
        this.entityData.define(PLAYER_UUID_STR, "");
    }

    // FIX: Anulăm ștergerea automată când playerul pleacă din zonă!
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public String getCategory() { return this.entityData.get(CATEGORY); }
    public void setCategory(String category) { this.entityData.set(CATEGORY, category); }

    public int getRank() { return this.entityData.get(RANK); }
    public void setRank(int rank) { this.entityData.set(RANK, rank); }

    public String getPlayerName() { return this.entityData.get(PLAYER_NAME); }
    public void setPlayerName(String name) { this.entityData.set(PLAYER_NAME, name); }

    public String getDisplayValue() { return this.entityData.get(DISPLAY_VALUE); }
    public void setDisplayValue(String val) { this.entityData.set(DISPLAY_VALUE, val); }

    public String getUUIDStr() { return this.entityData.get(PLAYER_UUID_STR); }
    public void setUUIDStr(String uuid) { this.entityData.set(PLAYER_UUID_STR, uuid); }

    @Override
    public void tick() {
        super.tick();
        this.setYBodyRot(this.getYRot());
        this.setYHeadRot(this.getYRot());
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entityIn) { }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TopCategory", getCategory());
        tag.putInt("TopRank", getRank());
        tag.putString("TopPlayerName", getPlayerName());
        tag.putString("TopDisplayValue", getDisplayValue());
        tag.putString("TopPlayerUUID", getUUIDStr());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCategory(tag.getString("TopCategory"));
        setRank(tag.getInt("TopRank"));
        setPlayerName(tag.getString("TopPlayerName"));
        setDisplayValue(tag.getString("TopDisplayValue"));
        setUUIDStr(tag.getString("TopPlayerUUID"));
    }
}