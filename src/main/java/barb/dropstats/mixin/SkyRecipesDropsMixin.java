package barb.dropstats.mixin;

import barb.dropstats.TooltipProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Optional bridge for SkyRecipes' chance text added to RRV slot tooltips after item callbacks. */
@Pseudo
@Mixin(targets = "com.github.kdgaming0.skyrecipes.rrv.recipe.client.SkyblockDropsClientRecipe", remap = false)
abstract class SkyRecipesDropsMixin {
    @ModifyArg(
            method = "buildChanceModifiers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
                    remap = false
            ),
            index = 0,
            require = 0,
            remap = false
    )
    private String dropstats$formatChance(String chanceText) {
        return TooltipProcessor.formatChanceText(chanceText);
    }
}
