package mod.grimmauld.windowlogging;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.DiggingParticle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.BooleanProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.*;
import net.minecraft.util.math.RayTraceContext.BlockMode;
import net.minecraft.util.math.RayTraceContext.FluidMode;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@SuppressWarnings("deprecation")
public class WindowInABlockBlock extends PaneBlock {

	public WindowInABlockBlock() {
		super(Properties.of(Material.STONE).noOcclusion());
	}

	private static void addBlockHitEffects(ParticleManager manager, BlockPos pos, BlockRayTraceResult target, BlockState blockstate, ClientWorld world) {
		VoxelShape shape = blockstate.getShape(world, pos);
		if (shape.isEmpty())
			return;
		Direction side = target.getDirection();
		int i = pos.getX();
		int j = pos.getY();
		int k = pos.getZ();
		AxisAlignedBB axisalignedbb = shape.bounds();
		double d0 = (double) i + manager.random.nextDouble() * (axisalignedbb.maxX - axisalignedbb.minX - (double) 0.2F) + (double) 0.1F + axisalignedbb.minX;
		double d1 = (double) j + manager.random.nextDouble() * (axisalignedbb.maxY - axisalignedbb.minY - (double) 0.2F) + (double) 0.1F + axisalignedbb.minY;
		double d2 = (double) k + manager.random.nextDouble() * (axisalignedbb.maxZ - axisalignedbb.minZ - (double) 0.2F) + (double) 0.1F + axisalignedbb.minZ;
		if (side == Direction.DOWN) {
			d1 = (double) j + axisalignedbb.minY - (double) 0.1F;
		}

		if (side == Direction.UP) {
			d1 = (double) j + axisalignedbb.maxY + (double) 0.1F;
		}

		if (side == Direction.NORTH) {
			d2 = (double) k + axisalignedbb.minZ - (double) 0.1F;
		}

		if (side == Direction.SOUTH) {
			d2 = (double) k + axisalignedbb.maxZ + (double) 0.1F;
		}

		if (side == Direction.WEST) {
			d0 = (double) i + axisalignedbb.minX - (double) 0.1F;
		}

		if (side == Direction.EAST) {
			d0 = (double) i + axisalignedbb.maxX + (double) 0.1F;
		}

		manager.add((new DiggingParticle(world, d0, d1, d2, 0.0D, 0.0D, 0.0D, blockstate)).init(pos).setPower(0.2F).scale(0.6F));
	}

	@Override
	public boolean hasTileEntity(BlockState state) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world) {
		return new WindowInABlockTileEntity();
	}

	@Override
	public boolean removedByPlayer(BlockState state, World world, BlockPos pos, PlayerEntity player,
								   boolean willHarvest, FluidState fluid) {
		if (player == null)
			return super.removedByPlayer(state, world, pos, null, willHarvest, fluid);

		Vector3d start = player.getEyePosition(1);
		ModifiableAttributeInstance reachDistanceAttribute = player.getAttribute(ForgeMod.REACH_DISTANCE.get());
		if (reachDistanceAttribute == null)
			return super.removedByPlayer(state, world, pos, null, willHarvest, fluid);
		Vector3d end = start.add(player.getLookAngle().scale(reachDistanceAttribute.getValue()));
		BlockRayTraceResult target =
			world.clip(new RayTraceContext(start, end, BlockMode.OUTLINE, FluidMode.NONE, player));
		WindowInABlockTileEntity tileEntity = getTileEntity(world, pos);
		if (tileEntity == null)
			return super.removedByPlayer(state, world, pos, player, willHarvest, fluid);
		BlockState windowBlock = tileEntity.getWindowBlock();
		CompoundNBT partialBlockTileData = tileEntity.getPartialBlockTileData();
		for (AxisAlignedBB bb : windowBlock.getShape(world, pos).toAabbs()) {
			if (bb.inflate(.1d).contains(target.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()))) {
				windowBlock.getBlock().playerWillDestroy(world, pos, windowBlock, player);
				if (!player.isCreative())
					Block.dropResources(windowBlock, world, pos, null, player, player.getMainHandItem());
				BlockState partialBlock = tileEntity.getPartialBlock();
				world.setBlockAndUpdate(pos, partialBlock);
				for (Direction d : Direction.values()) {
					BlockPos offset = pos.relative(d);
					BlockState otherState = world.getBlockState(offset);
					partialBlock = partialBlock.updateShape(d, otherState, world, pos, offset);
					world.sendBlockUpdated(offset, otherState, otherState, 2);
				}
				if (partialBlock != world.getBlockState(pos))
					world.setBlockAndUpdate(pos, partialBlock);
				if (world.getBlockState(pos).hasTileEntity()) {
					TileEntity te = world.getBlockEntity(pos);
					if (te != null) {
						te.deserializeNBT(partialBlockTileData);
						te.setChanged();
					}
				}
				return false;
			}
		}

		return super.removedByPlayer(state, world, pos, player, willHarvest, fluid);
	}

	@Override
	public boolean canBeReplaced(BlockState state, BlockItemUseContext useContext) {
		return false;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, IBlockReader reader, BlockPos pos) {
		return getSurroundingBlockState(reader, pos).propagatesSkylightDown(reader, pos);
	}

	@Override
	public boolean collisionExtendsVertically(BlockState state, IBlockReader world, BlockPos pos,
											  Entity collidingEntity) {
		return getSurroundingBlockState(world, pos).collisionExtendsVertically(world, pos, collidingEntity);
	}

	@Override
	public float getDestroyProgress(BlockState state, PlayerEntity player, IBlockReader worldIn, BlockPos pos) {
		return getSurroundingBlockState(worldIn, pos).getDestroyProgress(player, worldIn, pos);
	}

	@Override
	public float getExplosionResistance(BlockState state, IBlockReader world, BlockPos pos, Explosion explosion) {
		return getSurroundingBlockState(world, pos).getExplosionResistance(world, pos, explosion);
	}

	@Override
	public ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos,
								  PlayerEntity player) {
		BlockState window = getWindowBlockState(world, pos);
		for (AxisAlignedBB bb : window.getShape(world, pos).toAabbs()) {
			if (bb.inflate(.1d).contains(target.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ())))
				return window.getPickBlock(target, world, pos, player);
		}
		BlockState surrounding = getSurroundingBlockState(world, pos);
		return surrounding.getPickBlock(target, world, pos, player);
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
		TileEntity tileentity = builder.getOptionalParameter(LootParameters.BLOCK_ENTITY);
		if (!(tileentity instanceof WindowInABlockTileEntity))
			return Collections.emptyList();

		WindowInABlockTileEntity te = (WindowInABlockTileEntity) tileentity;
		TileEntity partialTE = te.getPartialBlockTileEntityIfPresent();
		if (partialTE != null)
			builder.withParameter(LootParameters.BLOCK_ENTITY, partialTE);
		List<ItemStack> drops = te.getPartialBlock().getDrops(builder);
		builder.withParameter(LootParameters.BLOCK_ENTITY, tileentity);
		drops.addAll(te.getWindowBlock().getDrops(builder));
		return drops;
	}

	@Override
	public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
		VoxelShape shape1 = getSurroundingBlockState(worldIn, pos).getShape(worldIn, pos, context);
		VoxelShape shape2 = getWindowBlockState(worldIn, pos).getShape(worldIn, pos, context);
		return VoxelShapes.or(shape1, shape2);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos,
										ISelectionContext context) {
		return getShape(state, worldIn, pos, context);
	}

	@Override
	public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn,
								  BlockPos currentPos, BlockPos facingPos) {
		WindowInABlockTileEntity te = getTileEntity(worldIn, currentPos);
		if (te == null)
			return stateIn;
		te.setWindowBlock(
			te.getWindowBlock().updateShape(facing, facingState, worldIn, currentPos, facingPos));
		BlockState blockState =
			te.getPartialBlock().updateShape(facing, facingState, worldIn, currentPos, facingPos);
		if (blockState.getBlock() instanceof FourWayBlock) {
			for (BooleanProperty side : Arrays.asList(FourWayBlock.EAST, FourWayBlock.NORTH, FourWayBlock.SOUTH,
				FourWayBlock.WEST))
				blockState = blockState.setValue(side, false);
			te.setPartialBlock(blockState);
		}
		te.requestModelDataUpdate();

		return stateIn;
	}

	public BlockState getSurroundingBlockState(IBlockReader reader, BlockPos pos) {
		WindowInABlockTileEntity te = getTileEntity(reader, pos);
		if (te != null)
			return te.getPartialBlock();
		return Blocks.AIR.defaultBlockState();
	}

	public BlockState getWindowBlockState(IBlockReader reader, BlockPos pos) {
		WindowInABlockTileEntity te = getTileEntity(reader, pos);
		if (te != null)
			return te.getWindowBlock();
		return Blocks.AIR.defaultBlockState();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
		return false;
	}

	@Nullable
	private WindowInABlockTileEntity getTileEntity(IBlockReader world, BlockPos pos) {
		TileEntity te = world.getBlockEntity(pos);
		if (te instanceof WindowInABlockTileEntity)
			return (WindowInABlockTileEntity) te;
		return null;
	}

	@Override
	public SoundType getSoundType(BlockState state, IWorldReader world, BlockPos pos, @Nullable Entity entity) {
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		return super.getSoundType(te != null ? te.getPartialBlock() : state, world, pos, entity);
	}

	@Override
	public boolean addLandingEffects(BlockState state1, ServerWorld world, BlockPos pos, BlockState state2, LivingEntity entity, int numberOfParticles) {
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		if (te != null) {
			te.getWindowBlock().addLandingEffects(world, pos, state2, entity, numberOfParticles / 2);
			return te.getPartialBlock().addLandingEffects(world, pos, state2, entity, numberOfParticles / 2);
		}
		return false;
	}

	@Override
	public boolean addRunningEffects(BlockState state, World world, BlockPos pos, Entity entity) {
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		if (te != null) {
			te.getWindowBlock().addRunningEffects(world, pos, entity);
			return te.getPartialBlock().addRunningEffects(world, pos, entity);
		}
		return false;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean addDestroyEffects(BlockState state, World world, BlockPos pos, ParticleManager manager) {
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		if (te != null) {
			te.getWindowBlock().addDestroyEffects(world, pos, manager);
			manager.destroy(pos, te.getWindowBlock());
			return te.getPartialBlock().addDestroyEffects(world, pos, manager);
		}
		return false;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public boolean addHitEffects(BlockState state, World world, RayTraceResult target, ParticleManager manager) {
		if (target.getType() != RayTraceResult.Type.BLOCK || !(target instanceof BlockRayTraceResult) || !(world instanceof ClientWorld))
			return false;
		BlockPos pos = ((BlockRayTraceResult) target).getBlockPos();
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		if (te != null) {
			te.getWindowBlock().addHitEffects(world, target, manager);
			addBlockHitEffects(manager, pos, (BlockRayTraceResult) target, te.getWindowBlock(), (ClientWorld) world);
			return te.getPartialBlock().addHitEffects(world, target, manager);
		}
		return false;
	}

	@OnlyIn(Dist.CLIENT)
	public IBakedModel createModel(IBakedModel original) {
		return new WindowInABlockModel(original);
	}

	@Override
	public int getLightValue(BlockState state, IBlockReader world, BlockPos pos) {
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		if (te != null) {
			BlockState partialState = te.getPartialBlock();
			partialState.getBlock().getLightValue(partialState, world, pos);
		}
		return 0;
	}

	@Nullable
	@Override
	public float[] getBeaconColorMultiplier(BlockState state, IWorldReader world, BlockPos pos, BlockPos beaconPos) {
		WindowInABlockTileEntity te = getTileEntity(world, pos);
		if (te != null) {
			BlockState windowState = te.getWindowBlock();
			return windowState.getBlock().getBeaconColorMultiplier(windowState, world, pos, beaconPos);
		}
		return super.getBeaconColorMultiplier(state, world, pos, beaconPos);
	}
}
