package test.mixin;
import java.util.ArrayList;
@Mixin(ArrayList.class)
public abstract class BadMixin {
    // wrong name (like a method removed between versions)
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void a(float f, long l, Object p, CallbackInfo ci) {}
    // right name, wrong descriptor
    @Inject(method = "get(F)Ljava/lang/Object;", at = @At("HEAD"))
    private void b(float f, CallbackInfoReturnable<Object> cir) {}
    // INVOKE target that no longer exists (like PoseStack.mulPose -> rotate)
    @Inject(method = "clear", at = @At(value = "INVOKE", target = "Ljava/util/Arrays;mulPose(Lorg/joml/Quaternionf;)V"))
    private void c(CallbackInfo ci) {}
    // owner class missing
    @Redirect(method = "clear", at = @At(value = "INVOKE", target = "Lnet/minecraft/Gone;foo()V"))
    private void d() {}
    // parameters do not match target get(int)
    @Inject(method = "get", at = @At("HEAD"))
    private void e(String wrong, CallbackInfoReturnable<Object> cir) {}
    @Accessor("nothere") abstract int getNothere();
    @Shadow private int missingField;
}
