package com.craisinlord.antos.content.block;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ComputerBlock extends BaseEntityBlock {
    public static final MapCodec<ComputerBlock> CODEC = Block.simpleCodec(ComputerBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public ComputerBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? 7 : 0));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<ComputerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return isAuthenticated(level, pos) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    private static boolean isAuthenticated(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ComputerBlockEntity computer && computer.isAuthenticated();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        net.minecraft.world.entity.Entity breaker = builder.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (breaker instanceof Player) return java.util.List.of();
        java.util.List<ItemStack> drops = super.getDrops(state, builder);
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ComputerBlockEntity computer) {
            for (ItemStack drop : drops) {
                if (drop.is(AntOSObjects.COMPUTER_ITEM.get())) {
                    com.craisinlord.antos.content.item.ComputerItem.storeComputerState(drop, computer, builder.getLevel().registryAccess());
                }
            }
        }
        return drops;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof ComputerBlockEntity computer) {
            ItemStack portableComputer = new ItemStack(AntOSObjects.COMPUTER_ITEM.get());
            com.craisinlord.antos.content.item.ComputerItem.storeComputerState(portableComputer, computer, level.registryAccess());
            if (!player.addItem(portableComputer)) player.drop(portableComputer, false);
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state, AntOSObjects.COMPUTER_BLOCK_ENTITY);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, AntOSObjects.COMPUTER_BLOCK_ENTITY.get(), ComputerBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
            if (level.isClientSide) {
                if (stack.is(AntOSObjects.FLOPPY_DISK.get())) {
                    return ItemInteractionResult.SUCCESS;
                }
            } else if (computer.insert(stack, player)) {
                return ItemInteractionResult.CONSUME;
            } else if (stack.is(AntOSObjects.FLOPPY_DISK.get()) && computer.requiresAccountSession()
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && com.craisinlord.antos.content.network.AnternetAccountHandler.session(serverPlayer) == null) {
                com.craisinlord.antos.content.network.AnternetAccountHandler.requestDiskInsertion(serverPlayer, computer, hand, stack);
                return ItemInteractionResult.SUCCESS;
            } else if (stack.is(AntOSObjects.FLOPPY_DISK.get()) && computer.requiresAccountSession()) {
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                                    BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ComputerBlockEntity computer) {
            computer.activate();
            if (level.isClientSide) {
                AntOSClientHooks.openComputer();
            } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.craisinlord.antos.content.network.AnternetAccountHandler.requestDeviceBinding(serverPlayer, computer);
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
}


