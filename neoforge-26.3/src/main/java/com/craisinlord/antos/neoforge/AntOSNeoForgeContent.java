package com.craisinlord.antos.neoforge;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.block.ComputerBlock;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.item.ComputerItem;
import com.craisinlord.antos.content.item.AntroidPhoneItem;
import com.craisinlord.antos.content.item.FloppyDiskItem;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AntOSNeoForgeContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AntOS.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AntOS.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AntOS.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AntOS.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, AntOS.MODID);

    public static final DeferredHolder<Block, ComputerBlock> COMPUTER = BLOCKS.register("computer",
            () -> new ComputerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AntOS.MODID, "computer")))));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> FLOPPY_DISK_COMPONENT = COMPONENTS.register("floppy_disk",
            () -> DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("computer",
            () -> new BlockEntityType<>((pos, state) -> new ComputerBlockEntity(pos, state, AntOSNeoForgeContent::computerBlockEntityType), COMPUTER.get()));
    public static final DeferredHolder<Item, Item> FLOPPY_DISK = ITEMS.register("floppy_disk", () -> new FloppyDiskItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AntOS.MODID, "floppy_disk")))));
    public static final DeferredHolder<Item, ComputerItem> COMPUTER_ITEM = ITEMS.register("computer", () -> new ComputerItem(COMPUTER.get(), new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AntOS.MODID, "computer")))));
    public static final DeferredHolder<Item, AntroidPhoneItem> ANTROID_PHONE = ITEMS.register("antroid_phone", () -> new AntroidPhoneItem(new Item.Properties().stacksTo(1).setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AntOS.MODID, "antroid_phone")))));
    public static final DeferredHolder<EntityType<?>, EntityType<RewardDropEntity>> REWARD_DROP_ENTITY = ENTITY_TYPES.register("reward_drop",
            () -> EntityType.Builder.of(RewardDropEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(2)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(AntOS.MODID, "reward_drop"))));

    private AntOSNeoForgeContent() {}

    private static BlockEntityType<ComputerBlockEntity> computerBlockEntityType() {
        return COMPUTER_BLOCK_ENTITY.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(AntOSNeoForgeContent::addCreativeItems);
        AntOSObjects.bind(FLOPPY_DISK::get, FLOPPY_DISK_COMPONENT::get, COMPUTER_BLOCK_ENTITY::get, COMPUTER_ITEM::get,
                REWARD_DROP_ENTITY::get, ANTROID_PHONE::get);
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(COMPUTER_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(FLOPPY_DISK.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ANTROID_PHONE.get());
    }
}
