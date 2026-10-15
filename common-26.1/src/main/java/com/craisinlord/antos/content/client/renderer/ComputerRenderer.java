package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import com.craisinlord.antos.content.client.model.ComputerModel;
import net.minecraft.core.Direction;
import com.geckolib.renderer.GeoBlockRenderer;

public final class ComputerRenderer extends GeoBlockRenderer<ComputerBlockEntity, net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> {
    public ComputerRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        super(context, new ComputerModel());
    }

    @Override
    protected Direction getBlockStateDirection(ComputerBlockEntity computer) {
        return computer.getBlockState().getValue(com.craisinlord.antos.content.block.ComputerBlock.FACING);
    }
}


