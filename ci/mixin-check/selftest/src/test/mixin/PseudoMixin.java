package test.mixin;
@Pseudo
@Mixin(targets = "org.vivecraft.Absent")
public abstract class PseudoMixin {
    @Inject(method = "whatever", at = @At("HEAD")) private void a(CallbackInfo ci) {}
}
