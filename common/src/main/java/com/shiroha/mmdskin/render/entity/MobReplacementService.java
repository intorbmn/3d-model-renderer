package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.asset.catalog.ModelCatalogEntry;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.UIConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 解析原版生物到 MMD 模型的本地替换映射。
 */
public final class MobReplacementService {

    private MobReplacementService() {
    }

    public static String getReplacementModelName(LivingEntity entity) {
        // A mob given its own model with the model tag wears it, whatever its kind is set to.
        if (entity != null && !isPlayerLike(entity) && !isMaidEntity(entity)) {
            String own = com.shiroha.mmdskin.companion.CompanionState.model(entity.getUUID());
            if (own != null && ModelCatalogEntry.findByFolderName(own) != null) {
                return own;
            }
        }
        if (entity == null || !MobReplacementTargets.isSupported(entity.getType()) || isPlayerLike(entity) || isMaidEntity(entity)) {
            return null;
        }

        Identifier entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityTypeId == null) {
            return null;
        }

        String modelName = ConfigManager.getMobModelReplacement(entityTypeId.toString());
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }

        return ModelCatalogEntry.findByFolderName(modelName) != null ? modelName : null;
    }

    public static boolean isMaidEntity(LivingEntity entity) {
        String className = entity.getClass().getName();
        return className.contains("EntityMaid") || className.contains("touhoulittlemaid");
    }

    private static boolean isPlayerLike(LivingEntity entity) {
        Identifier entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return Identifier.fromNamespaceAndPath("minecraft", "player").equals(entityTypeId);
    }
}
