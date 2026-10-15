package com.craisinlord.antos.forge;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.block.ComputerBlock;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import com.craisinlord.antos.content.item.AntroidPhoneItem;
import com.craisinlord.antos.content.item.ComputerItem;
import com.craisinlord.antos.content.item.FloppyDiskItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AntOSForgeContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AntOS.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AntOS.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AntOS.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, AntOS.MODID);

    public static final RegistryObject<ComputerBlock> COMPUTER = BLOCKS.register("computer",
            () -> new ComputerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final RegistryObject<BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("computer",
            () -> BlockEntityType.Builder.of((pos, state) -> new ComputerBlockEntity(pos, state, AntOSForgeContent::computerBlockEntityType), COMPUTER.get()).build(null));
    public static final RegistryObject<Item> FLOPPY_DISK = ITEMS.register("floppy_disk", () -> new FloppyDiskItem(new Item.Properties()));
    public static final RegistryObject<ComputerItem> COMPUTER_ITEM = ITEMS.register("computer", () -> new ComputerItem(COMPUTER.get(), new Item.Properties()));
    public static final RegistryObject<AntroidPhoneItem> ANTROID_PHONE = ITEMS.register("antroid_phone", () -> new AntroidPhoneItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<EntityType<RewardDropEntity>> REWARD_DROP_ENTITY = ENTITY_TYPES.register("reward_drop",
            () -> EntityType.Builder.of(RewardDropEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(2).build("antos:reward_drop"));

    private AntOSForgeContent() {
    }

    private static BlockEntityType<ComputerBlockEntity> computerBlockEntityType() {
        return COMPUTER_BLOCK_ENTITY.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(AntOSForgeContent::addCreativeItems);
        AntOSObjects.bind(FLOPPY_DISK::get, COMPUTER_BLOCK_ENTITY::get, COMPUTER_ITEM::get,
                REWARD_DROP_ENTITY::get, ANTROID_PHONE::get);
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(COMPUTER_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(FLOPPY_DISK.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ANTROID_PHONE.get());
    }
}
