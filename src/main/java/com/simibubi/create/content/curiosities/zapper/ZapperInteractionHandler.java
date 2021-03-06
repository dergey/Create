package com.simibubi.create.content.curiosities.zapper;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.networking.AllPackets;
import com.simibubi.create.foundation.networking.NbtPacket;
import com.simibubi.create.foundation.utility.BlockHelper;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.StairsShape;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceContext.BlockMode;
import net.minecraft.util.math.RayTraceContext.FluidMode;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(value = Dist.CLIENT)
public class ZapperInteractionHandler {

	@SubscribeEvent
	public static void leftClickingTheZapperSelectsANewBlock(PlayerInteractEvent.LeftClickEmpty event) {
		ItemStack heldItem = event.getPlayer()
			.getHeldItemMainhand();
		if (heldItem.getItem() instanceof ZapperItem && trySelect(heldItem, event.getPlayer()))
			AllPackets.channel.sendToServer(new NbtPacket(heldItem, Hand.MAIN_HAND));
	}

	@SubscribeEvent
	public static void leftClickingBlocksWithTheZapperSelectsTheBlock(PlayerInteractEvent.LeftClickBlock event) {
		ItemStack heldItem = event.getPlayer()
			.getHeldItemMainhand();
		if (heldItem.getItem() instanceof ZapperItem && trySelect(heldItem, event.getPlayer())) {
			event.setCancellationResult(ActionResultType.FAIL);
			event.setCanceled(true);
			AllPackets.channel.sendToServer(new NbtPacket(heldItem, Hand.MAIN_HAND));
		}
	}

	public static boolean trySelect(ItemStack stack, PlayerEntity player) {
		if (player.isSneaking())
			return false;

		Vec3d start = player.getPositionVec()
			.add(0, player.getEyeHeight(), 0);
		Vec3d range = player.getLookVec()
			.scale(getRange(stack));
		BlockRayTraceResult raytrace = player.world
			.rayTraceBlocks(new RayTraceContext(start, start.add(range), BlockMode.OUTLINE, FluidMode.NONE, player));
		BlockPos pos = raytrace.getPos();
		if (pos == null)
			return false;

		player.world.sendBlockBreakProgress(player.getEntityId(), pos, -1);
		BlockState newState = player.world.getBlockState(pos);

		if (BlockHelper.getRequiredItem(newState)
			.isEmpty())
			return false;
		if (player.world.getTileEntity(pos) != null)
			return false;
		if (newState.has(BlockStateProperties.DOUBLE_BLOCK_HALF))
			return false;
		if (newState.has(BlockStateProperties.ATTACHED))
			return false;
		if (newState.has(BlockStateProperties.HANGING))
			return false;
		if (newState.has(BlockStateProperties.BED_PART))
			return false;
		if (newState.has(BlockStateProperties.STAIRS_SHAPE))
			newState = newState.with(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
		if (newState.has(BlockStateProperties.PERSISTENT))
			newState = newState.with(BlockStateProperties.PERSISTENT, true);

		CompoundNBT tag = stack.getOrCreateTag();
		if (tag.contains("BlockUsed") && NBTUtil.readBlockState(stack.getTag()
			.getCompound("BlockUsed")) == newState)
			return false;

		tag.put("BlockUsed", NBTUtil.writeBlockState(newState));
		player.world.playSound(player, player.getPosition(), AllSoundEvents.BLOCKZAPPER_CONFIRM.get(),
			SoundCategory.BLOCKS, 0.5f, 0.8f);
		return true;
	}

	public static int getRange(ItemStack stack) {
		if (stack.getItem() instanceof ZapperItem)
			return ((ZapperItem) stack.getItem()).getZappingRange(stack);
		return 0;
	}
}
