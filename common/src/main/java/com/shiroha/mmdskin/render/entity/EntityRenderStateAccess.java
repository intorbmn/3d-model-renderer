package com.shiroha.mmdskin.render.entity;

import net.minecraft.world.entity.Entity;

/**
 * 文件职责：让渲染状态携带产生它的实体。
 *
 * <p>Minecraft 26.3 splits entity rendering into extract and submit, and the render state handed to
 * submit no longer references the entity. The model pipeline still needs it (animation state, model
 * selection), so the platform mixins implement this on every entity render state.
 */
public interface EntityRenderStateAccess {
    void mmdskin$capture(Entity entity, float partialTick);

    /** The entity this state was extracted from, or null if it was not extracted by an entity renderer. */
    Entity mmdskin$entity();

    float mmdskin$partialTick();
}
