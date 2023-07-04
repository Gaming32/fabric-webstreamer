package fr.theorozier.webstreamer.mixin;

import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.DisplayBlockInteract;
import fr.theorozier.webstreamer.display.screen.DisplayBlockScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin implements DisplayBlockInteract {

    @SuppressWarnings("all")
    private LocalPlayer getThis() {
        return (LocalPlayer) (Object) this;
    }

    @Override
    public void openDisplayBlockScreen(DisplayBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(new DisplayBlockScreen(blockEntity));
    }

}
