package com.craisinlord.antos.content;

import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.entity.RewardDropEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public final class AntOSObjects {
    public static Supplier<Item> FLOPPY_DISK = missing("floppy disk");
    public static Supplier<DataComponentType<ResourceLocation>> FLOPPY_DISK_COMPONENT = missing("floppy disk data component");
    public static Supplier<BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY = missing("computer block entity type");
    public static Supplier<Item> COMPUTER_ITEM = missing("computer item");
    public static Supplier<EntityType<RewardDropEntity>> REWARD_DROP_ENTITY = missing("reward drop entity");

    public static void bind(Supplier<Item> disk, Supplier<DataComponentType<ResourceLocation>> diskComponent,
                            Supplier<BlockEntityType<ComputerBlockEntity>> computerBlockEntity, Supplier<Item> computerItem,
                            Supplier<EntityType<RewardDropEntity>> rewardDropEntity) {
        FLOPPY_DISK = disk;
        FLOPPY_DISK_COMPONENT = diskComponent;
        COMPUTER_BLOCK_ENTITY = computerBlockEntity;
        COMPUTER_ITEM = computerItem;
        REWARD_DROP_ENTITY = rewardDropEntity;
    }

    private static <T> Supplier<T> missing(String name) {
        return () -> { throw new IllegalStateException("AntOS registry not initialized: " + name); };
    }
    private AntOSObjects() {}
}

