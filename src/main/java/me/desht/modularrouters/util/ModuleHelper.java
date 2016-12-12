package me.desht.modularrouters.util;

import me.desht.modularrouters.item.module.ItemModule;
import me.desht.modularrouters.item.module.Module;
import me.desht.modularrouters.logic.RouterRedstoneBehaviour;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Collection of static convenience methods for managing NBT data in a module itemstack.
 */
public class ModuleHelper {
    public static final String NBT_FLAGS = "Flags";
    public static final String NBT_REDSTONE_ENABLED = "RedstoneEnabled";
    public static final String NBT_REDSTONE_MODE = "RedstoneMode";
    public static final String NBT_REGULATOR_ENABLED = "RegulatorEnabled";
    public static final String NBT_REGULATOR_AMOUNT = "RegulatorAmount";
    public static final String NBT_FILTER = "ModuleFilter";
    private static final String NBT_OWNER = "Owner";


    @Nonnull
    public static NBTTagCompound validateNBT(ItemStack stack) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound == null) {
            stack.setTagCompound(compound = new NBTTagCompound());
        }
        if (compound.getTagId(NBT_FLAGS) != Constants.NBT.TAG_BYTE) {
            byte flags = 0x0;
            for (Module.ModuleFlags b : Module.ModuleFlags.values()) {
                if (b.getDefaultValue()) {
                    flags |= b.getMask();
                }
            }
            compound.setByte(NBT_FLAGS, flags);
        }
        if (compound.getTagId(NBT_FILTER) != Constants.NBT.TAG_LIST) {
            compound.setTag(NBT_FILTER, new NBTTagList());
        }
        return compound;
    }

    public static boolean isBlacklist(ItemStack stack) {
        return checkFlag(stack, Module.ModuleFlags.BLACKLIST);
    }

    public static boolean ignoreMeta(ItemStack stack) {
        return checkFlag(stack, Module.ModuleFlags.IGNORE_META);
    }

    public static boolean ignoreNBT(ItemStack stack) {
        return checkFlag(stack, Module.ModuleFlags.IGNORE_NBT);
    }

    public static boolean ignoreOreDict(ItemStack stack) {
        return checkFlag(stack, Module.ModuleFlags.IGNORE_OREDICT);
    }

    public static boolean terminates(ItemStack stack) {
        return checkFlag(stack, Module.ModuleFlags.TERMINATE);
    }

    public static boolean checkFlag(ItemStack stack, Module.ModuleFlags flag) {
        NBTTagCompound compound = validateNBT(stack);
        return (compound.getByte(NBT_FLAGS) & flag.getMask()) != 0x0;
    }

    public static Module.RelativeDirection getDirectionFromNBT(ItemStack stack) {
        Module module = ItemModule.getModule(stack);
        if (module == null || !module.isDirectional()) {
            return Module.RelativeDirection.NONE;
        }
        NBTTagCompound compound = validateNBT(stack);
        return Module.RelativeDirection.values()[(compound.getByte(NBT_FLAGS) & 0x70) >> 4];
    }

    public static boolean isRedstoneBehaviourEnabled(ItemStack stack) {
        NBTTagCompound compound = validateNBT(stack);
        return compound.getBoolean(NBT_REDSTONE_ENABLED);
    }

    public static boolean isRegulatorEnabled(ItemStack stack) {
        NBTTagCompound compound = validateNBT(stack);
        return compound.getBoolean(NBT_REGULATOR_ENABLED);
    }

    public static int getRegulatorAmount(ItemStack itemstack) {
        NBTTagCompound compound = validateNBT(itemstack);
        return compound.getInteger(NBT_REGULATOR_AMOUNT);
    }

    public static RouterRedstoneBehaviour getRedstoneBehaviour(ItemStack stack) {
        NBTTagCompound compound = validateNBT(stack);
        if (compound.getBoolean(NBT_REDSTONE_ENABLED)) {
            try {
                // check for mode stored as a string (v1.0), or as a byte (v1.1+)
                int id = compound.getTagId(NBT_REDSTONE_MODE);
                if (id == Constants.NBT.TAG_BYTE) {
                    return RouterRedstoneBehaviour.values()[compound.getByte(NBT_REDSTONE_MODE)];
                } else if (id == Constants.NBT.TAG_STRING) {
                    return RouterRedstoneBehaviour.valueOf(compound.getString(NBT_REDSTONE_MODE));
                } else {
                    return RouterRedstoneBehaviour.ALWAYS;
                }
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                return RouterRedstoneBehaviour.ALWAYS;
            }
        } else {
            return RouterRedstoneBehaviour.ALWAYS;
        }
    }

    public static void setRedstoneBehaviour(ItemStack stack, boolean enabled, RouterRedstoneBehaviour behaviour) {
        NBTTagCompound compound = validateNBT(stack);
        compound.setBoolean(NBT_REDSTONE_ENABLED, enabled);
        compound.setByte(NBT_REDSTONE_MODE, (byte) behaviour.ordinal());
    }

    public static void setRegulatorAmount(ItemStack stack, boolean enabled, int amount) {
        NBTTagCompound compound = validateNBT(stack);
        compound.setBoolean(NBT_REGULATOR_ENABLED, enabled);
        compound.setInteger(NBT_REGULATOR_AMOUNT, amount);
    }

    public static NBTTagList getFilterItems(ItemStack stack) {
        NBTTagCompound compound = validateNBT(stack);
        return compound.getTagList(NBT_FILTER, Constants.NBT.TAG_COMPOUND);
    }

    public static void setOwner(ItemStack stack, EntityPlayer player) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound == null) {
            compound = new NBTTagCompound();
        }
        NBTTagList owner = new NBTTagList();
        owner.appendTag(new NBTTagString(player.getDisplayNameString()));
        owner.appendTag(new NBTTagString(player.getUniqueID().toString()));
        compound.setTag(NBT_OWNER, owner);
        stack.setTagCompound(compound);
    }

    private static final Pair<String,UUID> NO_OWNER = Pair.of("", null);

    public static Pair<String, UUID> getOwnerNameAndId(ItemStack stack) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(NBT_OWNER)) {
            return NO_OWNER;
        }
        NBTTagList l = stack.getTagCompound().getTagList(NBT_OWNER, Constants.NBT.TAG_STRING);
        return Pair.of(l.getStringTagAt(0), UUID.fromString(l.getStringTagAt(1)));
    }

}
