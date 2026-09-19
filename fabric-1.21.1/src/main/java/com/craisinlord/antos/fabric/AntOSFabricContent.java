package com.craisinlord.antos.fabric;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.block.ComputerBlock;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.item.ComputerItem;
import com.craisinlord.antos.content.item.FloppyDiskItem;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import com.craisinlord.antos.content.block.ComputerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class AntOSFabricContent {
    public static DataComponentType<ResourceLocation> FLOPPY_DISK_COMPONENT;
    public static ComputerBlock COMPUTER;
    public static BlockEntityType<ComputerBlockEntity> COMPUTER_BLOCK_ENTITY;
    public static Item FLOPPY_DISK;
    public static Item COMPUTER_ITEM;
    public static EntityType<RewardDropEntity> REWARD_DROP_ENTITY;

    private AntOSFabricContent() {}

    public static void register() {
        if (COMPUTER != null) {
            return;
        }
        FLOPPY_DISK_COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                id("floppy_disk"), DataComponentType.<ResourceLocation>builder()
                        .persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());
        COMPUTER = Registry.register(BuiltInRegistries.BLOCK, id("computer"),
                new ComputerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        COMPUTER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("computer"),
                FabricBlockEntityTypeBuilder.create((pos, state) -> new ComputerBlockEntity(pos, state, () -> COMPUTER_BLOCK_ENTITY), COMPUTER).build());
        FLOPPY_DISK = Registry.register(BuiltInRegistries.ITEM, id("floppy_disk"), new FloppyDiskItem(new Item.Properties()));
        COMPUTER_ITEM = Registry.register(BuiltInRegistries.ITEM, id("computer"), new ComputerItem(COMPUTER, new Item.Properties()));
        REWARD_DROP_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("reward_drop"), EntityType.Builder
                .of(RewardDropEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(2).build("antos:reward_drop"));
        AntOSObjects.bind(() -> FLOPPY_DISK, () -> FLOPPY_DISK_COMPONENT, () -> COMPUTER_BLOCK_ENTITY, () -> COMPUTER_ITEM,
                () -> REWARD_DROP_ENTITY);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(COMPUTER_ITEM));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(FLOPPY_DISK));
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(AntOS.MODID, path); }
}

