package test.mixin;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ArrayList.class)
public abstract class GoodMixin {
    @Shadow private int size;
    @Inject(method = "size", at = @At("RETURN"), cancellable = true)
    private void a(CallbackInfoReturnable<Integer> cir) {}
    @Inject(method = "get(I)Ljava/lang/Object;", at = @At("HEAD"))
    private void b(int index, CallbackInfoReturnable<Object> cir) {}
    @Redirect(method = "clear", at = @At(value = "INVOKE", target = "Ljava/util/Arrays;fill([Ljava/lang/Object;Ljava/lang/Object;)V"))
    private void c(Object[] a, Object v) {}
    @Accessor("size") abstract int getSize();
}
