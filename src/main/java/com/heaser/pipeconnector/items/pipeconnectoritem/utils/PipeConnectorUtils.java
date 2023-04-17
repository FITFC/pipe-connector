package com.heaser.pipeconnector.items.pipeconnectoritem.utils;


import com.heaser.pipeconnector.constants.TagKeys;
import com.heaser.pipeconnector.items.pipeconnectoritem.PipeConnectorItem;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeConnectorUtils {

    private static final Logger LOGGER = LogUtils.getLogger();


    public static boolean connectPathWithSegments(Level level, BlockPos start, BlockPos end, int depth, Block block) {

        Set<BlockPos> blockPosSet = getBlockPosSet(start, end, depth);

        LOGGER.debug(blockPosSet.toString());
        Player player = Minecraft.getInstance().player;

        assert player != null;
        boolean isCreativeMode = player.isCreative();
        int pipeLimit = 640;

        // Disable pipe check and reduction in creative mode.
        if (!isCreativeMode) {
            int numOfPipes = getNumberOfPipesInInventory(player);
            if (numOfPipes < blockPosSet.size()) {
                int missingPipes = blockPosSet.size() - numOfPipes;
                LOGGER.debug("Not enough pipes in inventory, missing " + missingPipes + " pipes.");
                player.displayClientMessage(Component.translatable("item.pipe_connector.message.notEnoughPipes", missingPipes).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW), true);
                return false;
            }
            if (pipeLimit < blockPosSet.size()) {
                LOGGER.debug("Unable to place more than " + pipeLimit + " at once");
                player.displayClientMessage(Component.translatable("item.pipe_connector.message.reachedPipeLimit", pipeLimit).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW), true);
                return false;
            }
        }
        blockPosSet.forEach((blockPos -> {
            if (!isCreativeMode) {
                reduceNumberOfPipesInInventory(player);
            }
            breakAndSetBlock(level, blockPos, block);
        }));
        return true;
    }

    // -----------------------------------------------------------------------------------------------------------------

    public static BlockPos getNeighborInFacingDirection(BlockPos pos, Direction facing) {
        return pos.relative(facing);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static Set<BlockPos> getBlockPosSet(BlockPos start, BlockPos end, int depth) {
        Set<BlockPos> blockPosList = new HashSet<>();


        int deltaY = (start.getY() > end.getY()) ? Math.abs((start.getY() - end.getY())) : Math.abs(end.getY() - start.getY());
        int startDepth = depth, endDepth = depth;

        if (start.getY() > end.getY()) {
            endDepth -= deltaY;
        } else {
            startDepth -= deltaY;
        }

        for (int i = 0; i < startDepth; i++) {
            blockPosList.add(start);
            start = start.below();
        }

        for (int i = 0; i < endDepth; i++) {
            blockPosList.add(end);
            end = end.below();
        }

        // Create bridge
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();

        int xSteps = Math.abs(dx);
        int ySteps = Math.abs(dy);
        int zSteps = Math.abs(dz);

        int xDirection = dx > 0 ? 1 : -1;
        int yDirection = dy > 0 ? 1 : -1;
        int zDirection = dz > 0 ? 1 : -1;

        BlockPos currentPos = start.above();

        //         Move along the Y-axis
        for (int i = 0; i < ySteps; i++) {
            blockPosList.add(currentPos);
            currentPos = currentPos.offset(0, yDirection, 0);
        }

        // Move along the X-axis
        for (int i = 0; i < xSteps; i++) {
            blockPosList.add(currentPos);
            currentPos = currentPos.offset(xDirection, 0, 0);
        }

        // Move along the Z-axis
        for (int i = 0; i < zSteps; i++) {
            blockPosList.add(currentPos);
            currentPos = currentPos.offset(0, 0, zDirection);
        }

        return blockPosList;
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static void breakAndSetBlock(Level level, BlockPos pos, Block block) {
        if (isBreakable(level, pos)) {
            BlockState blockAtPos = level.getBlockState(pos);
            // TODO: Add logic for voiding specific blocks once isVoidableBlock works
            if(PipeConnectorUtils.isVoidableBlock(blockAtPos)) {
                    LOGGER.info("Voiding block at " + pos.toString());
            }
            level.destroyBlock(pos, true);
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static boolean isBreakable(Level level, BlockPos pos) {
        BlockState blockPos = level.getBlockState(pos);
        float hardness = blockPos.getDestroySpeed(level, pos);
        return hardness != -1;
    }

    // -----------------------------------------------------------------------------------------------------------------
    public static ItemStack holdingPipeConnector(Player player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND) != ItemStack.EMPTY && player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PipeConnectorItem) {
            return player.getItemInHand(InteractionHand.MAIN_HAND);
        }
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------------

    public static int getDepthFromStack(ItemStack stack) {
        if (stack.hasTag()) {
            return stack.getTag().getInt("Depth");
        }
        return 0; // Default depth value if the tag is missing
    }

    // -----------------------------------------------------------------------------------------------------------------

    public static void setDepthToStack(ItemStack stack, int depth) {
        if (!stack.hasTag()) {
            stack.setTag(new CompoundTag());
        }
        stack.getTag().putInt("Depth", depth);
    }

    // -----------------------------------------------------------------------------------------------------------------

    public static boolean holdingAllowedPipe(TagKey<Item> tagKey, Player player) {
        return player.getOffhandItem().is(tagKey);
    }


    // TODO(Heaser) - This is not working as intended, it always returns false, Pretty sure I've set the tags correctly, but who knows
    public static boolean isVoidableBlock(BlockState blockState) {

        boolean isVoidable = blockState.is(TagKeys.VOIDABLE_BLOCKS);
        LOGGER.info("isVoidable: " + isVoidable);

        return isVoidable;
    }


    // -----------------------------------------------------------------------------------------------------------------

    // Check how many items that were in the players offhand are also available in the players inventory
    public static int getNumberOfPipesInInventory(Player player) {
        Item pipe = player.getOffhandItem().getItem();
        AtomicInteger numberOfPipes = new AtomicInteger();
        numberOfPipes.set(player.getOffhandItem().getCount());
        Inventory inventory = player.getInventory();


        inventory.items.forEach((itemStack -> {
            if (itemStack.getItem() == pipe) {
                numberOfPipes.addAndGet(itemStack.getCount());
            }
        }));
        return numberOfPipes.get();
    }

    // -----------------------------------------------------------------------------------------------------------------

    // Reduce the number of pipes in the players inventory by one, start with the inventory and only finish with
    // the offhand if needed
    public static void reduceNumberOfPipesInInventory(Player player) {
        Item pipe = player.getOffhandItem().getItem();
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).getItem() == pipe) {
                if (inventory.items.get(i).getCount() > 1) {
                    inventory.items.get(i).shrink(1);
                    return;
                } else {
                    inventory.items.set(i, ItemStack.EMPTY);
                    return;
                }
            }
        }
        // Reduce the number of pipes in the players offhand by one
        if (player.getOffhandItem().getCount() > 1) {
            player.getOffhandItem().shrink(1);
        } else {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }
}
