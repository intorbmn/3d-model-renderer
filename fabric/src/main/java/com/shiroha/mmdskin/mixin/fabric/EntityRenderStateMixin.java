package com.shiroha.mmdskin.mixin.fabric;

import com.shiroha.mmdskin.render.entity.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给实体渲染状态附加来源实体与 partialTick，供提交阶段使用。 */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityRenderStateAccess {
    @Unique
    private Entity mmdskin$sourceEntity;

    @Unique
    private float mmdskin$sourcePartialTick;

    @Override
    public void mmdskin$capture(Entity entity, float partialTick) {
        this.mmdskin$sourceEntity = entity;
        this.mmdskin$sourcePartialTick = partialTick;
    }

    @Override
    public Entity mmdskin$entity() {
        return this.mmdskin$sourceEntity;
    }

    @Override
    public float mmdskin$partialTick() {
        return this.mmdskin$sourcePartialTick;
    }
}
