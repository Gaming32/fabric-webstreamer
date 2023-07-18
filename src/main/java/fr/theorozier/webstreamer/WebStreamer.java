package fr.theorozier.webstreamer;

import fr.theorozier.webstreamer.display.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebStreamer implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("WebStreamer");

    public static final Block DISPLAY_BLOCK = new DisplayBlock();
    public static final DisplayBlockItem DISPLAY_ITEM = new DisplayBlockItem(DISPLAY_BLOCK, new FabricItemSettings().tab(CreativeModeTab.TAB_REDSTONE));
    public static final BlockEntityType<DisplayBlockEntity> DISPLAY_BLOCK_ENTITY = FabricBlockEntityTypeBuilder.create(DisplayBlockEntity::new, DISPLAY_BLOCK).build();
    public static final ExtendedScreenHandlerType<DisplayMenu> DISPLAY_SCREEN_HANDLER = new ExtendedScreenHandlerType<>(DisplayMenu::new);

    @Override
    public void onInitialize() {

        Registry.register(Registry.BLOCK, "webstreamer:display", DISPLAY_BLOCK);
        Registry.register(Registry.ITEM, "webstreamer:display", DISPLAY_ITEM);
        Registry.register(Registry.BLOCK_ENTITY_TYPE, "webstreamer:display", DISPLAY_BLOCK_ENTITY);
        Registry.register(Registry.MENU, "webstreamer:display", DISPLAY_SCREEN_HANDLER);

        DisplayNetworking.registerDisplayUpdateReceiver();

        LOGGER.info("WebStreamer started.");

    }

}
