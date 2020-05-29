package com.simibubi.create.content.contraptions.components.structureMovement;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.components.actors.BlockBreakingMovementBehaviour;
import com.simibubi.create.foundation.collision.Matrix3d;
import com.simibubi.create.foundation.collision.OrientedBB;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.VecHelper;

import net.minecraft.block.BlockState;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.material.PushReaction;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.Direction.AxisDirection;
import net.minecraft.util.ReuseableStream;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.Template.BlockInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

public class ContraptionCollider {

	static Map<Object, AxisAlignedBB> renderedBBs = new HashMap<>();
	public static boolean wasClientPlayerGrounded;

	public static void collideEntities(ContraptionEntity contraptionEntity) {
		if (Contraption.isFrozen())
			return;
		if (!contraptionEntity.collisionEnabled())
			return;

		World world = contraptionEntity.getEntityWorld();
//		Vec3d contraptionMotion = contraptionEntity.getMotion();
		Contraption contraption = contraptionEntity.getContraption();
		AxisAlignedBB bounds = contraptionEntity.getBoundingBox();
		Vec3d contraptionPosition = contraptionEntity.getPositionVec();
		Vec3d contraptionRotation = contraptionEntity.getRotationVec();
		contraptionEntity.collidingEntities.clear();

		if (contraption == null)
			return;
		if (bounds == null)
			return;

		for (Entity entity : world.getEntitiesWithinAABB((EntityType<?>) null, bounds.grow(1),
			e -> canBeCollidedWith(e))) {
			if (entity instanceof PlayerEntity && !world.isRemote)
				return;

			Vec3d centerOf = VecHelper.getCenterOf(BlockPos.ZERO);
			Vec3d entityPosition = entity.getPositionVec();
			Vec3d position = entityPosition.subtract(contraptionPosition)
				.subtract(centerOf);
			position =
				VecHelper.rotate(position, -contraptionRotation.x, -contraptionRotation.y, -contraptionRotation.z);
			position = position.add(centerOf)
				.subtract(entityPosition);
			AxisAlignedBB localBB = entity.getBoundingBox()
				.offset(position)
				.grow(1.0E-7D);

			OrientedBB obb = new OrientedBB(localBB);
			if (!contraptionRotation.equals(Vec3d.ZERO)) {
				Matrix3d rotation = new Matrix3d().asIdentity();
				rotation.multiply(new Matrix3d().asXRotation(AngleHelper.rad(contraptionRotation.x)));
				rotation.multiply(new Matrix3d().asYRotation(AngleHelper.rad(contraptionRotation.y)));
				rotation.multiply(new Matrix3d().asZRotation(AngleHelper.rad(contraptionRotation.z)));
				obb.setRotation(rotation);
			}

			ReuseableStream<VoxelShape> potentialHits = getPotentiallyCollidedShapes(world, contraption, localBB);
			if (potentialHits.createStream()
				.count() == 0)
				continue;

			MutableBoolean onCollide = new MutableBoolean(true);
			potentialHits.createStream()
				.forEach(shape -> {
					AxisAlignedBB bb = shape.getBoundingBox();
					Vec3d intersect = obb.intersect(bb);
					if (intersect == null)
						return;
					intersect = VecHelper.rotate(intersect, contraptionRotation.x, contraptionRotation.y,
						contraptionRotation.z);

					obb.setCenter(obb.getCenter()
						.add(intersect));
					entity.move(MoverType.PISTON, intersect);

					Vec3d entityMotion = entity.getMotion();
					if (entityMotion.getX() > 0 == intersect.getX() < 0)
						entityMotion = entityMotion.mul(0, 1, 1);
					if (entityMotion.getY() > 0 == intersect.getY() < 0)
						entityMotion = entityMotion.mul(1, 0, 1);
					if (entityMotion.getZ() > 0 == intersect.getZ() < 0)
						entityMotion = entityMotion.mul(1, 1, 0);
					entity.setMotion(entityMotion);

					if (onCollide.isTrue()) {
						onCollide.setFalse();
						contraptionEntity.collidingEntities.add(entity);
						entity.velocityChanged = true;
					}

					if (intersect.y > 0) {
						entity.handleFallDamage(entity.fallDistance, 1);
						entity.fallDistance = 0;
						entity.onGround = true;
						DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> checkForClientPlayerCollision(entity));
					}

					if (entity instanceof ServerPlayerEntity)
						((ServerPlayerEntity) entity).connection.floatingTickCount = 0;
				});

//			Vec3d positionOffset = contraptionPosition.scale(-1);
//			AxisAlignedBB entityBB = entity.getBoundingBox()
//				.offset(positionOffset)
//				.grow(1.0E-7D);
//			Vec3d entityMotion = entity.getMotion();
//			Vec3d relativeMotion = entityMotion.subtract(contraptionMotion);
//			Vec3d allowedMovement = Entity.getAllowedMovement(relativeMotion, entityBB, world,
//				ISelectionContext.forEntity(entity), potentialHits);
//			potentialHits.createStream()
//				.forEach(voxelShape -> pushEntityOutOfShape(entity, voxelShape, positionOffset, contraptionMotion));
//
//
//			if (allowedMovement.equals(relativeMotion))
//				continue;
//
//			if (allowedMovement.y != relativeMotion.y) {
//				entity.handleFallDamage(entity.fallDistance, 1);
//				entity.fallDistance = 0;
//				entity.onGround = true;
//				DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> checkForClientPlayerCollision(entity));
//			}
//
//			if (entity instanceof ServerPlayerEntity)
//				((ServerPlayerEntity) entity).connection.floatingTickCount = 0;
//			if (entity instanceof PlayerEntity && !world.isRemote)
//				return;
//
//			entity.setMotion(allowedMovement.add(contraptionMotion));
		}

	}

	public static boolean canBeCollidedWith(Entity e) {
		if (e instanceof PlayerEntity && e.isSpectator())
			return false;
		if (e.noClip)
			return false;
		if (e instanceof IProjectile)
			return false;
		return e.getPushReaction() == PushReaction.NORMAL;
	}

	@OnlyIn(Dist.CLIENT)
	private static void checkForClientPlayerCollision(Entity entity) {
		if (entity != Minecraft.getInstance().player)
			return;
		wasClientPlayerGrounded = true;
	}

	public static void pushEntityOutOfShape(Entity entity, VoxelShape voxelShape, Vec3d positionOffset,
		Vec3d shapeMotion) {
		AxisAlignedBB entityBB = entity.getBoundingBox()
			.offset(positionOffset);
		Vec3d entityMotion = entity.getMotion();

		if (!voxelShape.toBoundingBoxList()
			.stream()
			.anyMatch(entityBB::intersects))
			return;

		AxisAlignedBB shapeBB = voxelShape.getBoundingBox();
		Direction bestSide = Direction.DOWN;
		double bestOffset = 100;
		double finalOffset = 0;

		for (Direction face : Direction.values()) {
			Axis axis = face.getAxis();
			double d = axis == Axis.X ? entityBB.getXSize() + shapeBB.getXSize()
				: axis == Axis.Y ? entityBB.getYSize() + shapeBB.getYSize() : entityBB.getZSize() + shapeBB.getZSize();
			d = d + .5f;

			Vec3d nudge = new Vec3d(face.getDirectionVec()).scale(d);
			AxisAlignedBB nudgedBB = entityBB.offset(nudge.getX(), nudge.getY(), nudge.getZ());
			double nudgeDistance = face.getAxisDirection() == AxisDirection.POSITIVE ? -d : d;
			double offset = voxelShape.getAllowedOffset(face.getAxis(), nudgedBB, nudgeDistance);
			double abs = Math.abs(nudgeDistance - offset);
			if (abs < Math.abs(bestOffset) && abs != 0) {
				bestOffset = abs;
				finalOffset = abs;
				bestSide = face;
			}
		}

		if (bestOffset != 0) {
			entity.move(MoverType.SELF, new Vec3d(bestSide.getDirectionVec()).scale(finalOffset));
			boolean positive = bestSide.getAxisDirection() == AxisDirection.POSITIVE;

			double clamped;
			switch (bestSide.getAxis()) {
			case X:
				clamped = positive ? Math.max(shapeMotion.x, entityMotion.x) : Math.min(shapeMotion.x, entityMotion.x);
				entity.setMotion(clamped, entityMotion.y, entityMotion.z);
				break;
			case Y:
				clamped = positive ? Math.max(shapeMotion.y, entityMotion.y) : Math.min(shapeMotion.y, entityMotion.y);
				if (bestSide == Direction.UP)
					clamped = shapeMotion.y;
				entity.setMotion(entityMotion.x, clamped, entityMotion.z);
				entity.handleFallDamage(entity.fallDistance, 1);
				entity.fallDistance = 0;
				entity.onGround = true;
				break;
			case Z:
				clamped = positive ? Math.max(shapeMotion.z, entityMotion.z) : Math.min(shapeMotion.z, entityMotion.z);
				entity.setMotion(entityMotion.x, entityMotion.y, clamped);
				break;
			}
		}
	}

	public static ReuseableStream<VoxelShape> getPotentiallyCollidedShapes(World world, Contraption contraption,
		AxisAlignedBB localBB) {
		AxisAlignedBB blockScanBB = localBB.grow(.5f);
		BlockPos min = new BlockPos(blockScanBB.minX, blockScanBB.minY, blockScanBB.minZ);
		BlockPos max = new BlockPos(blockScanBB.maxX, blockScanBB.maxY, blockScanBB.maxZ);

		ReuseableStream<VoxelShape> potentialHits = new ReuseableStream<>(BlockPos.getAllInBox(min, max)
			.filter(contraption.blocks::containsKey)
			.map(p -> {
				BlockState blockState = contraption.blocks.get(p).state;
				BlockPos pos = contraption.blocks.get(p).pos;
				VoxelShape collisionShape = blockState.getCollisionShape(world, p);
				return collisionShape.withOffset(pos.getX(), pos.getY(), pos.getZ());
			}));

		return potentialHits;
	}

	public static boolean collideBlocks(ContraptionEntity contraptionEntity) {
		if (Contraption.isFrozen())
			return true;
		if (!contraptionEntity.collisionEnabled())
			return false;

		World world = contraptionEntity.getEntityWorld();
		Vec3d motion = contraptionEntity.getMotion();
		Contraption contraption = contraptionEntity.getContraption();
		AxisAlignedBB bounds = contraptionEntity.getBoundingBox();
		Vec3d position = contraptionEntity.getPositionVec();
		BlockPos gridPos = new BlockPos(position);

		if (contraption == null)
			return false;
		if (bounds == null)
			return false;
		if (motion.equals(Vec3d.ZERO))
			return false;

		Direction movementDirection = Direction.getFacingFromVector(motion.x, motion.y, motion.z);

		// Blocks in the world
		if (movementDirection.getAxisDirection() == AxisDirection.POSITIVE)
			gridPos = gridPos.offset(movementDirection);
		if (isCollidingWithWorld(world, contraption, gridPos, movementDirection))
			return true;

		// Other moving Contraptions
		for (ContraptionEntity otherContraptionEntity : world.getEntitiesWithinAABB(ContraptionEntity.class,
			bounds.grow(1), e -> !e.equals(contraptionEntity))) {

			if (!otherContraptionEntity.collisionEnabled())
				continue;

			Vec3d otherMotion = otherContraptionEntity.getMotion();
			Contraption otherContraption = otherContraptionEntity.getContraption();
			AxisAlignedBB otherBounds = otherContraptionEntity.getBoundingBox();
			Vec3d otherPosition = otherContraptionEntity.getPositionVec();

			if (otherContraption == null)
				return false;
			if (otherBounds == null)
				return false;

			if (!bounds.offset(motion)
				.intersects(otherBounds.offset(otherMotion)))
				continue;

			for (BlockPos colliderPos : contraption.getColliders(world, movementDirection)) {
				colliderPos = colliderPos.add(gridPos)
					.subtract(new BlockPos(otherPosition));
				if (!otherContraption.blocks.containsKey(colliderPos))
					continue;
				return true;
			}
		}

		return false;
	}

	public static boolean isCollidingWithWorld(World world, Contraption contraption, BlockPos anchor,
		Direction movementDirection) {
		for (BlockPos pos : contraption.getColliders(world, movementDirection)) {
			BlockPos colliderPos = pos.add(anchor);

			if (!world.isBlockPresent(colliderPos))
				return true;

			BlockState collidedState = world.getBlockState(colliderPos);
			BlockInfo blockInfo = contraption.blocks.get(pos);

			if (blockInfo.state.getBlock() instanceof IPortableBlock) {
				IPortableBlock block = (IPortableBlock) blockInfo.state.getBlock();
				if (block.getMovementBehaviour() instanceof BlockBreakingMovementBehaviour) {
					BlockBreakingMovementBehaviour behaviour =
						(BlockBreakingMovementBehaviour) block.getMovementBehaviour();
					if (!behaviour.canBreak(world, colliderPos, collidedState)
						&& !collidedState.getCollisionShape(world, pos)
							.isEmpty()) {
						return true;
					}
					continue;
				}
			}

			if (AllBlocks.PULLEY_MAGNET.has(collidedState) && pos.equals(BlockPos.ZERO)
				&& movementDirection == Direction.UP)
				continue;
			if (collidedState.getBlock() instanceof CocoaBlock)
				continue;
			if (!collidedState.getMaterial()
				.isReplaceable()
				&& !collidedState.getCollisionShape(world, colliderPos)
					.isEmpty()) {
				return true;
			}

		}
		return false;
	}

}
