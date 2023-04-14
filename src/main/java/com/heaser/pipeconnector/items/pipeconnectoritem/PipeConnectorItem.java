package com.heaser.pipeconnector.items.pipeconnectoritem;

import com.heaser.pipeconnector.items.pipeconnectoritem.utils.PipeConnectorUtils;
import com.heaser.pipeconnector.network.NetworkHandler;
import com.heaser.pipeconnector.network.UpdateDepthPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;


import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

import java.util.List;

public class PipeConnectorItem extends Item {
    private BlockPos firstPosition;
    private BlockPos secondPosition;
    private static final Logger LOGGER = LogUtils.getLogger();
    Direction facingSideEnd;
    Direction facingSideStart;



    public PipeConnectorItem(Properties properties) {
        super(properties);
    }


    // -----------------------------------------------------------------------------------------------------------------



    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand useHand) {

        BlockPos playerLookingAt = player.blockPosition().relative(player.getDirection());
        boolean isAir = level.getBlockState(playerLookingAt).isAir();
        boolean isShiftKeyDown = player.isShiftKeyDown();

        if (!level.isClientSide() &&
                useHand == InteractionHand.MAIN_HAND && isShiftKeyDown && isAir) {
            player.sendSystemMessage(Component.literal("Resetting Positions"));
            resetBlockPosFirstAndSecondPositions();
        }
        return super.use(level, player, useHand);
    }

    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide) {
            ItemStack stack = context.getItemInHand();
            int depth = PipeConnectorUtils.getDepthFromStack(stack);
            boolean isShiftKeyDown = context.getPlayer().isShiftKeyDown();

            BlockPos clickedPosition = context.getClickedPos();
            if(isShiftKeyDown && context.getClickedFace() == Direction.UP) {
                context.getPlayer().sendSystemMessage(Component.literal("Connecting Pipe from top side is not allowed"));
                return InteractionResult.FAIL;

            }

            if (firstPosition == null && isShiftKeyDown) {
                firstPosition = clickedPosition;
                context.getPlayer().sendSystemMessage(Component.literal("1st position chosen at: " + firstPosition));
                LOGGER.debug("firstPosition found: {}", firstPosition);
                facingSideStart = context.getClickedFace();
            } else if (secondPosition == null && isShiftKeyDown) {
                if(clickedPosition.equals(firstPosition)) {
                    resetBlockPosFirstAndSecondPositions();
                    context.getPlayer().sendSystemMessage(Component.literal("Resetting Positions"));
                    return InteractionResult.SUCCESS;
                }

                secondPosition = clickedPosition;
                facingSideEnd = context.getClickedFace();
                context.getPlayer().sendSystemMessage(Component.literal("2nd position chosen at: " + secondPosition));
                LOGGER.debug("secondPosition found: {}", secondPosition);
                if (secondPosition != null && firstPosition != null) {
                    connectBlocks(context.getLevel(), firstPosition, secondPosition, depth);
                    resetBlockPosFirstAndSecondPositions();
                }

            }
        }
        return InteractionResult.SUCCESS;
    }

    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag tooltipFlag) {

        // Depth will be displayed as depth-1 to the player to reduce confusion
        int depth = PipeConnectorUtils.getDepthFromStack(stack) - 1;

        if (Screen.hasShiftDown()) {

            components.add(Component.literal("Shift + Right-Click on Blocks will set positions").withStyle(ChatFormatting.DARK_AQUA));
            components.add(Component.literal("Shift + Right-Click on Air will cancel selection").withStyle(ChatFormatting.BLUE));
            components.add(Component.literal("Shift + Scrollwheel to change Pipe depth").withStyle(ChatFormatting.LIGHT_PURPLE));

        } else {
            components.add(Component.literal("Hold Shift for more info").withStyle(ChatFormatting.GOLD));
        }
        super.appendHoverText(stack, level, components, tooltipFlag);
    }

    // -----------------------------------------------------------------------------------------------------------------

    public void connectBlocks(Level level, BlockPos start, BlockPos end, int depth) {

        BlockPos adjustedStart = PipeConnectorUtils.getNeighborInFacingDirection(start, facingSideStart);
        BlockPos adjustedEnd = PipeConnectorUtils.getNeighborInFacingDirection(end, facingSideEnd);

        PipeConnectorUtils.connectPathWithSegments(level, adjustedStart, adjustedEnd, depth);
    }

    private void resetBlockPosFirstAndSecondPositions() {
        firstPosition = null;
        secondPosition = null;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // A function that returns whether a block in a specific BlockPos is an AIR block or not.
    private boolean isAirBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }



}

