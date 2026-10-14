package cn.daenx.mc.emeraldeconomy.block;

import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 功能机器方块（需求文档开头说明 + 各节 1.1 / 3.1 / 4.1 / 5.1 / 6.1）。
 * <p>
 * 关键设计：
 * <ul>
 *     <li><b>尺寸 1×1×2</b>：参考原版门的做法，用 {@code HALF} 属性把两个方块格子拼成一台机器。
 *         下半格负责渲染整体模型，上半格是不可见的实心方块，因此确实占用了两个方块的位置。</li>
 *     <li><b>需要红石供电</b>：只有下半格正下方是红石块时才能使用；否则右键提示「未接通红石电源」。</li>
 *     <li>机器本身不保存状态，所有经济数据都在 {@code EconomyData} 里按玩家 / 全服保存。</li>
 * </ul>
 */
public class MachineBlock extends Block {

    /** 上半格 / 下半格。 */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** 机器的朝向，让正面（屏幕）对着放置它的玩家。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 用于消除双重掉落的方块更新标记（与门一致：35 = 3 | 32，带「不触发邻居比较器」）。 */
    private static final int UPDATE_FLAGS = 35;

    private final MachineType machineType;

    public MachineBlock(MachineType machineType, Properties properties) {
        super(properties);
        this.machineType = machineType;
        registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH));
    }

    public MachineType machineType() {
        return machineType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    // ------------------------------------------------------------------
    // 放置与拆除
    // ------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        // 需要上方至少有一格可替换空间，否则放不下 1×1×2
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState()
                    .setValue(HALF, DoubleBlockHalf.LOWER)
                    .setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * 两个半格必须成对存在，任一被破坏时另一格自动消失。
     * <p>
     * 逻辑与原版门一致：邻居是另一个半格就同步属性，否则返回空气。
     * 返回空气会走 {@code Block#updateOrDestroy}，以「会掉落」的方式移除另一半。
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean upperLookingUp = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (!upperLookingUp) {
            // 下半格的下方方块被换掉不影响使用（我们允许放在任意方块上）
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        }
        return neighborState.is(this) && neighborState.getValue(HALF) != half
                ? neighborState.setValue(HALF, half)
                : Blocks.AIR.defaultBlockState();
    }

    /**
     * 玩家破坏方块前的处理（照原版门的做法）。
     * <p>
     * 战利品表只给下半格掉落（条件 {@code half=lower}），两种破坏方式各自掉落恰好一份：
     * <ul>
     *     <li>破坏<b>下半格</b>：它自己的战利品表掉一份；上半格随后被移除，不掉东西。</li>
     *     <li>破坏<b>上半格</b>：上半格的战利品表不掉东西，下半格由 {@link #updateShape} 以
     *         「会掉落」的方式移除 —— 所以正好一份。</li>
     * </ul>
     * <b>创造模式</b>例外：原版破坏半格方块在创造模式下不该掉东西，而下半格会被邻居更新
     * 带着掉落，所以这里先把下半格静默移除（{@link #UPDATE_FLAGS} 带「不掉落」位）。
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && state.getValue(HALF) == DoubleBlockHalf.UPPER
                && player.isCreative()) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * 上半格不参与渲染。
     * <p>
     * 整台机器的外观全部由<b>下半格</b>的模型负责（模型元素可以画到 y=32，
     * 也就是画到上面那一格的高度），上半格只负责占住第二个方块的位置、挡住玩家。
     * <p>
     * 这里必须显式返回 {@link RenderShape#INVISIBLE}：如果让上半格去渲染一个「空模型」，
     * 拿不到任何面的模型会退化成 missing 模型，于是上半格会变成一个品红-黑格子方块。
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        // 任意方块上都能放
        return true;
    }

    // ------------------------------------------------------------------
    // 右键交互
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        return openScreen(level, pos, state, player);
    }

    /**
     * 手持物品右键。
     * <p>
     * 这里刻意做成「交互优先、潜行让位」——和原版箱子、工作台的行为一致：
     * 玩家手上随便拿着什么方块，右键机器都应该打开界面；
     * 想在这台机器旁边垒方块，按住潜行再右键即可。
     * <p>
     * （反过来的写法会让「手上拿着石头右键 ATM」变成糊一块石头上去，界面打不开。）
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        InteractionResult result = openScreen(level, pos, state, player);
        return result == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 校验供电并让服务端通知客户端打开界面。 */
    private InteractionResult openScreen(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide) {
            // 客户端只负责挥手动画，真正的逻辑在服务端
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!isPowered(level, basePos)) {
            Notify.sendPrefixed(serverPlayer, "⚡ ", "message.no_power");
            return InteractionResult.SUCCESS;
        }
        NetworkHandler.openScreen(serverPlayer, machineType.screen());
        return InteractionResult.SUCCESS;
    }

    /**
     * 掉落统一由<b>下半格</b>负责（战利品表也只认 {@code half=lower}）。
     * <p>
     * 为什么要在代码里再拦一道：整机有两格，玩家挖哪一格都可能触发「另一格被连带移除」，
     * 两边各自走一次战利品表就会掉两份。这里规定只有下半格会真正走掉落，
     * 于是两种情况都恰好一份：
     * <ul>
     *     <li>挖<b>下半格</b>：这里掉一份；上半格随后被移除时状态是 {@code upper}，被本方法挡掉。</li>
     *     <li>挖<b>上半格</b>：上半格被挡掉；下半格被连带移除时状态是 {@code lower}，
     *         走这里掉一份（此时 {@code player} 为 null，战利品表不依赖玩家）。</li>
     * </ul>
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            super.playerDestroy(level, player, pos, state, blockEntity, tool);
        }
    }

    /** 机器只有放在红石块正上方时才能正常工作。 */
    public boolean isPowered(BlockGetter level, BlockPos basePos) {
        return level.getBlockState(basePos.below()).is(Blocks.REDSTONE_BLOCK);
    }
}
