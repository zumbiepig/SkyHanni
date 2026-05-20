package at.hannibal2.skyhanni.mixins.transformers;

import at.hannibal2.skyhanni.features.garden.farming.GardenCustomKeybinds;
import at.hannibal2.skyhanni.features.garden.sensitivity.SensitivityReducer;
import at.hannibal2.skyhanni.utils.DelayedRun;
import at.hannibal2.skyhanni.utils.compat.MouseCompat;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(MouseHandler.class)
public class MixinMouse {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "onMove", at = @At("RETURN"))
    private void onMouseButton(long window, double x, double y, CallbackInfo ci) {
        MouseCompat.INSTANCE.setDeltaMouseX(this.accumulatedDX);
        MouseCompat.INSTANCE.setDeltaMouseY(this.accumulatedDY);
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MouseCompat.INSTANCE.setScroll(vertical);
        DelayedRun.INSTANCE.runNextTickOld(() -> {
            MouseCompat.INSTANCE.setScroll(0);
            return null;
        });
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        MouseCompat.INSTANCE.handleMouseButton(input, action);
    }

    @Inject(
        method = "handleAccumulatedMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z")
        // locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onMouseButtonHead(CallbackInfo ci, @Local(ordinal = 0) double timeDelta) {
        MouseCompat.INSTANCE.setTimeDelta(timeDelta * 10000);
    }

    @Inject(
        method = "grabMouse",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setAll()V")
    )
    private void onRestoreKeyStateAfterMouseGrab(CallbackInfo ci) {
        GardenCustomKeybinds.onMouseGrabRestoringKeyState();
    }

    @ModifyExpressionValue(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;sensitivity()Lnet/minecraft/client/OptionInstance;")
        )
    )
    private Object modifyMouseSensitivity(Object original) {
        return SensitivityReducer.remapSensitivity((Double) original);
    }
}
