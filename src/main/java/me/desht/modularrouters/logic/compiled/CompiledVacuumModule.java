package me.desht.modularrouters.logic.compiled;

import me.desht.modularrouters.block.tile.TileEntityItemRouter;
import me.desht.modularrouters.config.Config;
import me.desht.modularrouters.integration.IntegrationHandler;
import me.desht.modularrouters.item.module.Module;
import me.desht.modularrouters.item.module.VacuumModule;
import me.desht.modularrouters.item.upgrade.ItemUpgrade;
import me.desht.modularrouters.logic.ModuleTarget;
import me.desht.modularrouters.util.InventoryUtils;
import me.desht.modularrouters.util.ModuleHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public class CompiledVacuumModule extends CompiledModule {
    private static final int XP_FLUID_RATIO = 20;  // 1 xp = 20mb xp juice

    private final boolean fastPickup;
    private final boolean xpMode;
    private final FluidStack xpJuiceStack;

    // temporary small xp buffer (generally around an orb or less)
    // does not survive router recompilation...
    private int xpBuffered = 0;

    public CompiledVacuumModule(TileEntityItemRouter router, ItemStack stack) {
        super(router, stack);
        fastPickup = ModuleHelper.hasFastPickup(stack);
        xpMode = ModuleHelper.hasXPVacuum(stack);
        if (IntegrationHandler.fluidXpJuice != null) {
            xpJuiceStack = new FluidStack(IntegrationHandler.fluidXpJuice, 1000);
        } else {
            xpJuiceStack = null;
        }
    }

    @Override
    public boolean execute(TileEntityItemRouter router) {
        if (xpMode) {
            return handleXpMode(router);
        } else {
            return handleItemMode(router);
        }
    }

    private boolean handleItemMode(TileEntityItemRouter router) {
        if (router.isBufferFull()) {
            return false;
        }

        ItemStack bufferStack = router.getBuffer().getStackInSlot(0);

        int range = VacuumModule.getVacuumRange(router);
        BlockPos centrePos = getTarget().pos;
        List<EntityItem> items = router.getWorld().getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(centrePos.add(-range, -range, -range), centrePos.add(range + 1, range + 1, range + 1)));

        int toPickUp = router.getItemsPerTick();

        for (EntityItem item : items) {
            if (item.isDead || (!fastPickup && item.cannotPickup())) {
                continue;
            }
            ItemStack stackOnGround = item.getEntityItem();
            if ((bufferStack.isEmpty() || ItemHandlerHelper.canItemStacksStack(stackOnGround, bufferStack)) && getFilter().test(stackOnGround)) {
                int inRouter = bufferStack.getCount();
                int spaceInRouter = getRegulationAmount() > 0 ?
                        Math.min(stackOnGround.getMaxStackSize(), getRegulationAmount()) - inRouter :
                        stackOnGround.getMaxStackSize() - inRouter;
                ItemStack vacuumed = stackOnGround.splitStack(Math.min(router.getItemsPerTick(), spaceInRouter));
                ItemStack excess = router.insertBuffer(vacuumed);
                int remaining = excess == null ? 0 : excess.getCount();
                stackOnGround.grow(remaining);
                int inserted = vacuumed.getCount() - remaining;
                toPickUp -= inserted;
                if (stackOnGround.isEmpty()) {
                    item.setDead();
                }
                if (inserted > 0 && Config.vacuumParticles && router.getUpgradeCount(ItemUpgrade.UpgradeType.MUFFLER) < 2) {
                    ((WorldServer) router.getWorld()).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, false, item.posX, item.posY + 0.25, item.posZ, 2, 0.0, 0.0, 0.0, 0.0);
                }
                if (toPickUp <= 0) {
                    break;
                }
            }
        }
        return toPickUp < router.getItemsPerTick();
    }

    private boolean handleXpMode(TileEntityItemRouter router) {
        int range = VacuumModule.getVacuumRange(router);
        BlockPos centrePos = getTarget().pos;
        List<EntityXPOrb> orbs = router.getWorld().getEntitiesWithinAABB(EntityXPOrb.class,
                new AxisAlignedBB(centrePos.add(-range, -range, -range), centrePos.add(range + 1, range + 1, range + 1)));

        ItemStack inRouterStack = router.getBufferItemStack();

        XPMethod xpMethod = XPMethod.NONE;
        int spaceForXp = 0;

        IFluidHandler xpHandler = null;
        if (inRouterStack == null || inRouterStack.getItem() == Items.EXPERIENCE_BOTTLE && inRouterStack.stackSize < inRouterStack.getMaxStackSize()) {
            xpMethod = XPMethod.BOTTLES;
            // bottles yield 3-11 experience, so an average of 7
            spaceForXp = inRouterStack == null ?
                    64 * 7 : (inRouterStack.getMaxStackSize() - inRouterStack.stackSize) * 7;
        } else if (xpJuiceStack != null && inRouterStack.stackSize == 1) {
            xpHandler = FluidUtil.getFluidHandler(inRouterStack);
            if (xpHandler != null) {
                for (IFluidTankProperties tank : xpHandler.getTankProperties()) {
                    if (tank.canFillFluidType(xpJuiceStack)) {
                        spaceForXp += (tank.getCapacity() - (tank.getContents() == null ? 0 : tank.getContents().amount)) / XP_FLUID_RATIO;
                    }
                }
                xpMethod = XPMethod.XPJUICE;
            }
        }
        if (xpMethod == XPMethod.NONE) {
            return false;
        }

        int initialSpaceForXp = spaceForXp;
        for (EntityXPOrb orb : orbs) {
            System.out.println("router has space for " + spaceForXp + "xp, found orb with " + orb.getXpValue() + "xp");
            if (orb.getXpValue() > spaceForXp) {
                break;
            }
            switch (xpMethod) {
                case BOTTLES:
                    xpBuffered += orb.getXpValue();
                    if (xpBuffered > 7) {
                        ItemStack bottleStack = new ItemStack(Items.EXPERIENCE_BOTTLE, xpBuffered / 7);
                        ItemStack excess = router.insertBuffer(bottleStack);
                        xpBuffered -= bottleStack.stackSize * 7;
                        if (excess != null) {
                            InventoryUtils.dropItems(router.getWorld(), router.getPos(), excess);
                        }
                    }
                    break;
                case XPJUICE:
                    FluidStack xpStack = new FluidStack(IntegrationHandler.fluidXpJuice, orb.getXpValue() * XP_FLUID_RATIO + xpBuffered);
                    int filled = xpHandler.fill(xpStack, true);
                    if (filled < xpStack.amount) {
                        System.out.println(" - tank overflow?");
                        spaceForXp = 0;
                        xpBuffered = xpStack.amount - filled;
                    } else {
                        xpBuffered = 0;
                    }
                    break;
            }
            spaceForXp -= orb.getXpValue();
            orb.setDead();
        }

        return initialSpaceForXp - spaceForXp > 0;
    }

    @Override
    public ModuleTarget setupTarget(TileEntityItemRouter router, ItemStack stack) {
        if (router == null) {
            return null;
        }
        Module.RelativeDirection dir = getDirection();
        int offset = dir == Module.RelativeDirection.NONE ? 0 : VacuumModule.getVacuumRange(router) + 1;
        EnumFacing facing = router.getAbsoluteFacing(dir);
        return new ModuleTarget(router.getWorld().provider.getDimension(), router.getPos().offset(facing, offset), facing);
    }

    private enum XPMethod {
        NONE,     // no action
        BOTTLES,  // convert to bottles o' enchanting
        XPJUICE   // convert to xp juice (EnderIO)
    }
}
