package com.kkllffaa.meteor_litematica_printer;

import java.util.*;
import java.util.function.Supplier;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import static com.kkllffaa.meteor_litematica_printer.Utils.*;

public class Printer extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRendering = settings.createGroup("Rendering");

    // region settings
    private final Setting<Integer> printing_range = sgGeneral.add(new IntSetting.Builder()
			.name("printing-range")
			.description("The block place range.")
			.defaultValue(2)
			.min(1).sliderMin(1)
			.max(6).sliderMax(6)
			.build()
	);

	private final Setting<Integer> printing_delay = sgGeneral.add(new IntSetting.Builder()
			.name("printing-delay")
			.description("Delay between printing blocks in ticks.")
			.defaultValue(2)
			.min(0).sliderMin(0)
			.max(100).sliderMax(40)
			.build()
	);

	private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
			.name("blocks/tick")
			.description("How many blocks are placed per tick.")
			.defaultValue(1)
			.min(1).sliderMin(1)
			.max(100).sliderMax(100)
			.build()
	);

	private final Setting<Boolean> advanced = sgGeneral.add(new BoolSetting.Builder()
			.name("advanced")
			.description("Respect block rotation (places blocks in weird places in singleplayer, multiplayer should work fine).")
			.defaultValue(false)
			.build()
	);

	private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
			.name("air-place")
			.description("Allow the bot to place in the air.")
			.defaultValue(true)
			.build()
	);

	private final Setting<Boolean> placeThroughWall = sgGeneral.add(new BoolSetting.Builder()
			.name("Place Through Wall")
			.description("Allow the bot to place through walls.")
			.defaultValue(true)
			.build()
	);

	private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
			.name("swing")
			.description("Swing hand when placing.")
			.defaultValue(false)
			.build()
	);

    private final Setting<Boolean> returnHand = sgGeneral.add(new BoolSetting.Builder()
			.name("return-slot")
			.description("Return to the old slot.")
			.defaultValue(false)
			.build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
			.name("rotate")
			.description("Rotate to the blocks being placed.")
			.defaultValue(false)
			.build()
    );

    private final Setting<Boolean> clientSide = sgGeneral.add(new BoolSetting.Builder()
			.name("Client side Rotation")
			.description("Rotate to the blocks being placed on the client side.")
			.defaultValue(false)
			.visible(rotate::get)
			.build()
    );

	private final Setting<Boolean> dirtgrass = sgGeneral.add(new BoolSetting.Builder()
			.name("dirt-as-grass")
			.description("Use dirt instead of grass.")
			.defaultValue(true)
			.build()
	);

    private final Setting<SortAlgorithm> firstAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortAlgorithm>()
			.name("first-sorting-mode")
			.description("The blocks you want to place first.")
			.defaultValue(SortAlgorithm.None)
			.build()
	);

    private final Setting<SortingSecond> secondAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortingSecond>()
			.name("second-sorting-mode")
			.description("Second pass of sorting e.g., place the first blocks higher and closest to you.")
			.defaultValue(SortingSecond.None)
			.visible(()-> firstAlgorithm.get().applySecondSorting)
			.build()
	);

    private final Setting<Boolean> whitelistenabled = sgWhitelist.add(new BoolSetting.Builder()
			.name("whitelist-enabled")
			.description("Only place selected blocks.")
			.defaultValue(false)
			.build()
	);

    private final Setting<List<Block>> whitelist = sgWhitelist.add(new BlockListSetting.Builder()
			.name("whitelist")
			.description("Blocks to place.")
			.visible(whitelistenabled::get)
			.build()
	);

    private final Setting<Boolean> renderBlocks = sgRendering.add(new BoolSetting.Builder()
        .name("render-placed-blocks")
        .description("Renders block placements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fadeTime = sgRendering.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Time for the rendering to fade, in ticks.")
        .defaultValue(3)
        .min(1).sliderMin(1)
        .max(1000).sliderMax(20)
        .visible(renderBlocks::get)
        .build()
    );

    private final Setting<SettingColor> colour = sgRendering.add(new ColorSetting.Builder()
        .name("colour")
        .description("The cube color.")
        .defaultValue(new SettingColor(95, 190, 255))
        .visible(renderBlocks::get)
        .build()
    );

    private final Setting<Boolean> smoothRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-rotation")
        .description("Smoothly rotate to the block being placed.")
        .defaultValue(true)
        .visible(rotate::get)
        .build()
    );
    // endregion

    private Rotation currentRotation = null;
    private Rotation targetRotation = null;
    private int timer;
    private int usedSlot = -1;
    private final List<BlockPos> toSort = new ArrayList<>();
    private final List<Pair<Integer, BlockPos>> placed_fade = new ArrayList<>();

	public Printer() {
		super(Addon.CATEGORY, "litematica-printer", "Automatically prints open schematics");
	}

    @Override
    public void onActivate() {
        onDeactivate();
    }

	@Override
    public void onDeactivate() {
		placed_fade.clear();
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
        if (smoothRotation.get() && targetRotation != null && mc.player != null) {
            if (currentRotation == null) {
                currentRotation = new Rotation(mc.player.getYaw(), mc.player.getPitch()).normalizeAndClamp();
            }

            float yawStep = 5f;
            float pitchStep = 5f;

            float newYaw = smoothApproach(currentRotation.yaw(), targetRotation.yaw(), yawStep);
            float newPitch = smoothApproach(currentRotation.pitch(), targetRotation.pitch(), pitchStep);

            currentRotation = new Rotation(newYaw, newPitch).normalizeAndClamp();

            mc.player.setYaw(currentRotation.yaw());
            mc.player.setPitch(currentRotation.pitch());

            if (currentRotation.isReallyCloseTo(targetRotation)) {
                targetRotation = null;
            }
        }

		if (mc.player == null || mc.world == null) {
			placed_fade.clear();
			return;
		}

		placed_fade.forEach(s -> s.setLeft(s.getLeft() - 1));
		placed_fade.removeIf(s -> s.getLeft() <= 0);

		WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
		if (worldSchematic == null) {
			placed_fade.clear();
			toggle();
			return;
		}

		toSort.clear();


		if (timer >= printing_delay.get()) {
			BlockIterator.register(printing_range.get() + 1, printing_range.get() + 1, (pos, blockState) -> {
				BlockState required = worldSchematic.getBlockState(pos);

				if (
						mc.player.getBlockPos().isWithinDistance(pos, printing_range.get())
						&& blockState.isReplaceable()
						&& !required.isLiquid()
						&& !required.isAir()
						&& blockState.getBlock() != required.getBlock()
						&& DataManager.getRenderLayerRange().isPositionWithinRange(pos)
						&& !mc.player.getBoundingBox().intersects(Vec3d.of(pos), Vec3d.of(pos).add(1, 1, 1))
						&& required.canPlaceAt(mc.world, pos)
					) {
					boolean isBlockInLineOfSight = isBlockInLineOfSight(pos, required);
			    	SlabType wantedSlabType = advanced.get() && required.contains(Properties.SLAB_TYPE) ? required.get(Properties.SLAB_TYPE) : null;
			    	BlockHalf wantedBlockHalf = advanced.get() && required.contains(Properties.BLOCK_HALF) ? required.get(Properties.BLOCK_HALF) : null;
			    	Direction wantedHorizontalOrientation = advanced.get() && required.contains(Properties.HORIZONTAL_FACING) ? required.get(Properties.HORIZONTAL_FACING) : null;
			    	Axis wantedAxies = advanced.get() && required.contains(Properties.AXIS) ? required.get(Properties.AXIS) : null;
			    	Direction wantedHopperOrientation = advanced.get() && required.contains(Properties.HOPPER_FACING) ? required.get(Properties.HOPPER_FACING) : null;

					if(
						airPlace.get()
						&& placeThroughWall.get()
						|| !airPlace.get()
						&& !placeThroughWall.get()
						&&  isBlockInLineOfSight
						&& getVisiblePlaceSide(
							pos,
							required,
							wantedSlabType,
							wantedBlockHalf,
							wantedHorizontalOrientation != null ? wantedHorizontalOrientation : wantedHopperOrientation,
							wantedAxies,
							printing_range.get(),
							advanced.get() ? dir(required) : null
						) != null
						|| airPlace.get()
						&& !placeThroughWall.get()
						&& isBlockInLineOfSight
						|| !airPlace.get()
						&& placeThroughWall.get()
						&& BlockUtils.getPlaceSide(pos) != null
					) {
						if (!whitelistenabled.get() || whitelist.get().contains(required.getBlock())) {
							toSort.add(new BlockPos(pos));
						}
					}
				}
			});

			BlockIterator.after(() -> {
				if (firstAlgorithm.get() != SortAlgorithm.None) {
					if (firstAlgorithm.get().applySecondSorting) {
						if (secondAlgorithm.get() != SortingSecond.None) {
							toSort.sort(secondAlgorithm.get().algorithm);
						}
					}
					toSort.sort(firstAlgorithm.get().algorithm);
				}


				int placed = 0;
                for (BlockPos pos : toSort) {
                    BlockState state = worldSchematic.getBlockState(pos);
                    Item item = state.getBlock().asItem();

                    if (dirtgrass.get() && item == Items.GRASS_BLOCK) item = Items.DIRT;

                    boolean success;

                    if (mc.player.getAbilities().creativeMode) {
                        success = placeWithPacket(state, pos);
                    } else {
                        success = switchItem(item, state, () -> place(state, pos));
                    }

                    if (success) {
                        timer = 0;
                        placed++;
                        if (renderBlocks.get()) {
                            placed_fade.add(new Pair<>(fadeTime.get(), new BlockPos(pos)));
                        }
                        if (placed >= bpt.get()) return;
                    }
                }
            });


		} else timer++;
	}

    public boolean placeWithPacket(BlockState required, BlockPos pos) {
        if (mc.player == null || mc.world == null || !mc.player.getAbilities().creativeMode) return false;

        Item item = required.getBlock().asItem();
        if (item == Items.AIR) return false;

        int slot = mc.player.getInventory().getSelectedSlot();
        int inventorySlot = 36 + slot;

        ItemStack original = mc.player.getInventory().getStack(slot).copy();

        ItemStack stack = item.getDefaultStack();
        stack.set(DataComponentTypes.BLOCK_STATE, new BlockStateComponent(encodeBlockStateAsComponent(required)));

        mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(inventorySlot, stack));

        Direction facing = dir(required);
        Vec3d hitVec = Vec3d.ofCenter(pos).add(Vec3d.of(facing.getVector()).multiply(0.5));
        BlockHitResult hitResult = new BlockHitResult(hitVec, facing, pos, false);

        mc.interactionManager.sendSequencedPacket(mc.world, sequence ->
            new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence)
        );

        mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(inventorySlot, original));

        return true;
    }

    public boolean place(BlockState required, BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;

        Direction wantedSide = advanced.get() ? dir(required) : null;
        SlabType wantedSlabType = advanced.get() && required.contains(Properties.SLAB_TYPE) ? required.get(Properties.SLAB_TYPE) : null;
        BlockHalf wantedBlockHalf = advanced.get() && required.contains(Properties.BLOCK_HALF) ? required.get(Properties.BLOCK_HALF) : null;
        Direction wantedHorizontalOrientation = advanced.get() && required.contains(Properties.HORIZONTAL_FACING) ? required.get(Properties.HORIZONTAL_FACING) : null;
        Axis wantedAxies = advanced.get() && required.contains(Properties.AXIS) ? required.get(Properties.AXIS) : null;
        Direction wantedHopperOrientation = advanced.get() && required.contains(Properties.HOPPER_FACING) ? required.get(Properties.HOPPER_FACING) : null;
        Direction wantedFace = advanced.get() && required.contains(Properties.FACING) ? required.get(Properties.FACING) : null;

        Direction placeFacing = wantedFace != null ? wantedFace : (wantedHorizontalOrientation != null ? wantedHorizontalOrientation : wantedHopperOrientation);

        Direction placeSide = placeThroughWall.get()
            ? getPlaceSide(pos, required, wantedSlabType, wantedBlockHalf, placeFacing, wantedAxies, wantedSide)
            : getVisiblePlaceSide(pos, required, wantedSlabType, wantedBlockHalf, placeFacing, wantedAxies, printing_range.get(), wantedSide);

        if (rotate.get() && smoothRotation.get()) {
            Vec3d eyes = mc.player.getEyePos();
            Vec3d target = Vec3d.ofCenter(pos);

            if (placeFacing != null) {
                target = target.add(Vec3d.of(placeFacing.getVector()).multiply(0.5));
            }

            double dx = target.x - eyes.x;
            double dy = target.y - eyes.y;
            double dz = target.z - eyes.z;
            double dist = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

            targetRotation = new Rotation(yaw, pitch).normalizeAndClamp();
        }

        return Utils.place(pos, placeSide, wantedSlabType, wantedBlockHalf, placeFacing, wantedAxies, airPlace.get(), swing.get(), rotate.get(), clientSide.get(), printing_range.get());
    }

    private boolean switchItem(Item item, BlockState state, Supplier<Boolean> action) {
        if (mc.player == null) return false;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        boolean isCreative = mc.player.getAbilities().creativeMode;
        FindItemResult result = InvUtils.find(item);

        // ✅ Creative対応：持ってなくても置けるように最初にチェック！
        if (isCreative && !result.found()) {
            int slot = 0;
            FindItemResult fir = InvUtils.find(ItemStack::isEmpty, 0, 8);
            if (fir.found()) slot = fir.slot();

            ItemStack stack = item.getDefaultStack();
            stack.set(DataComponentTypes.BLOCK_STATE, new BlockStateComponent(encodeBlockStateAsComponent(state)));

            mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(36 + slot, stack));
            InvUtils.swap(slot, returnHand.get());

            if (action.get()) {
                usedSlot = slot;
                return true;
            } else {
                InvUtils.swap(selectedSlot, returnHand.get());
                return false;
            }
        }

        if (mc.player.getMainHandStack().getItem() == item) {
            if (action.get()) {
                usedSlot = mc.player.getInventory().getSelectedSlot();
                return true;
            } else return false;
        }

        if (usedSlot != -1 && mc.player.getInventory().getStack(usedSlot).getItem() == item) {
            InvUtils.swap(usedSlot, returnHand.get());
            if (action.get()) return true;
            InvUtils.swap(selectedSlot, returnHand.get());
            return false;
        }

        if (result.found()) {
            if (result.isHotbar()) {
                InvUtils.swap(result.slot(), returnHand.get());
                if (action.get()) {
                    usedSlot = mc.player.getInventory().getSelectedSlot();
                    return true;
                } else {
                    InvUtils.swap(selectedSlot, returnHand.get());
                    return false;
                }
            } else if (result.isMain()) {
                FindItemResult empty = InvUtils.findEmpty();
                if (empty.found() && empty.isHotbar()) {
                    InvUtils.move().from(result.slot()).toHotbar(empty.slot());
                    InvUtils.swap(empty.slot(), returnHand.get());
                    if (action.get()) {
                        usedSlot = mc.player.getInventory().getSelectedSlot();
                        return true;
                    } else {
                        InvUtils.swap(selectedSlot, returnHand.get());
                        return false;
                    }
                } else if (usedSlot != -1) {
                    InvUtils.move().from(result.slot()).toHotbar(usedSlot);
                    InvUtils.swap(usedSlot, returnHand.get());
                    if (action.get()) return true;
                    else {
                        InvUtils.swap(selectedSlot, returnHand.get());
                        return false;
                    }
                }
            }
        }

        return false;
    }

    private Direction dir(@NotNull BlockState state) {
		if (state.contains(Properties.FACING)) return state.get(Properties.FACING);
		else if (state.contains(Properties.AXIS)) return Direction.from(state.get(Properties.AXIS), Direction.AxisDirection.POSITIVE);
		else if (state.contains(Properties.HORIZONTAL_AXIS)) return Direction.from(state.get(Properties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
		else return Direction.UP;
	}

	@EventHandler
	private void onRender(Render3DEvent event) {
		placed_fade.forEach(s -> {
			Color a = new Color(colour.get().r, colour.get().g, colour.get().b, (int) (((float)s.getLeft() / (float) fadeTime.get()) * colour.get().a));
			event.renderer.box(s.getRight(), a, null, ShapeMode.Sides, 0);
		});
	}
}
