package de.mchelden;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

@Mod(McHelden.MOD_ID)
public class McHelden {
    public static final String MOD_ID = "mchelden";
    private static final String HEARTS = "mchelden_hearts";
    private static final String BAN_UNTIL = "mchelden_ban_until";
    private static final String SAFE_UNTIL = "mchelden_safe_until";
    private static final String LOOT = "mchelden_saved_loot";
    private static final String INITIALIZED = "mchelden_initialized";

    private static final long BAN_TICKS = 24L * 60L * 60L * 20L;
    private static final long SAFE_TICKS = 20L * 60L * 20L;

    public McHelden() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            init(p);
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        init(p);

        long banUntil = p.getPersistentData().getLong(BAN_UNTIL);
        if (banUntil > p.serverLevel().getGameTime()) {
            p.connection.disconnect(Component.literal("§cDu bist noch gesperrt. §7Restzeit: §e"
                    + formatTicks(banUntil - p.serverLevel().getGameTime())));
            return;
        }

        if (banUntil != 0) {
            p.getPersistentData().putLong(BAN_UNTIL, 0);
            restore20PercentLoot(p);
            setHearts(p, 1);
            p.getPersistentData().putLong(SAFE_UNTIL, p.serverLevel().getGameTime() + SAFE_TICKS);
            p.sendSystemMessage(Component.literal("§aWiedereinstieg! §7Du hast §e1 Herz§7 und bist §b20 Minuten geschützt§7."));
        }
        updateMaxHealth(p);
    }

    private void init(ServerPlayer p) {
        if (!p.getPersistentData().getBoolean(INITIALIZED)) {
            p.getPersistentData().putBoolean(INITIALIZED, true);
            setHearts(p, 2);
            updateMaxHealth(p);
        }
    }

    @SubscribeEvent
    public void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;
        if (victim.level().isClientSide()) return;

        if (!(e.getSource().getEntity() instanceof Player killer)) return;
        if (!(killer instanceof ServerPlayer attacker)) return;

        if (isProtected(victim)) {
            e.setCanceled(true);
            victim.setHealth(Math.min(victim.getMaxHealth(), 2.0f));
            return;
        }

        int oldHearts = getHearts(victim);
        int newHearts = Math.max(0, oldHearts - 1);
        setHearts(victim, newHearts);
        setHearts(attacker, getHearts(attacker) + 1);

        if (newHearts <= 0) {
            save20PercentSourceLoot(victim);
            long until = victim.serverLevel().getGameTime() + BAN_TICKS;
            victim.getPersistentData().putLong(BAN_UNTIL, until);
            victim.connection.disconnect(Component.literal("§cDu bist ausgeschieden! §7Wiedereinstieg in §e24 Stunden§7."));
            victim.server.getPlayerList().getBans().add(
                new net.minecraft.server.players.UserBanListEntry(
                    victim.getGameProfile(), new Date(System.currentTimeMillis() + 24L*60L*60L*1000L),
                    "MC Helden", "24h Ausschluss", null
                )
            );
        }

        updateMaxHealth(victim);
        updateMaxHealth(attacker);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            updateMaxHealth(p);
            if (isProtected(p)) {
                p.setInvulnerable(true);
            } else if (p.isInvulnerable()) {
                p.setInvulnerable(false);
            }
        }
    }

    private boolean isProtected(ServerPlayer p) {
        return p.getPersistentData().getLong(SAFE_UNTIL) > p.serverLevel().getGameTime();
    }

    private int getHearts(ServerPlayer p) {
        return Math.max(0, p.getPersistentData().getInt(HEARTS));
    }

    private void setHearts(ServerPlayer p, int hearts) {
        p.getPersistentData().putInt(HEARTS, Math.max(0, hearts));
        updateMaxHealth(p);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private void updateMaxHealth(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return;
        a.setBaseValue(Math.max(2.0, getHearts(p) * 2.0));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private void save20PercentSourceLoot(ServerPlayer p) {
        ListTag saved = new ListTag();
        Random r = new Random();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack stack = p.getInventory().getItem(i);
            if (!stack.isEmpty() && r.nextDouble() < 0.20) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Slot", i);
                tag.put("Item", stack.save(p.registryAccess()));
                saved.add(tag);
            }
        }
        p.getPersistentData().put(LOOT, saved);
    }

    private void restore20PercentLoot(ServerPlayer p) {
        Tag t = p.getPersistentData().get(LOOT);
        if (!(t instanceof ListTag list)) return;

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.parse(p.registryAccess(), entry.getCompound("Item")).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                if (!p.getInventory().add(stack)) p.drop(stack, false);
            }
        }
        p.getPersistentData().remove(LOOT);
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("helden")
            .then(Commands.literal("herzen")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Component.literal("§eDu hast §c" + getHearts(p) + " Herzen§e."), false);
                    return 1;
                }))
            .then(Commands.literal("setherzen")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("value", net.minecraft.commands.arguments.IntegerArgumentType.integer(0, 100)))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int h = net.minecraft.commands.arguments.IntegerArgumentType.getInteger(c, "value");
                    setHearts(p, h);
                    return 1;
                })));
    }

    private String formatTicks(long ticks) {
        long sec = Math.max(0, ticks / 20);
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return h + "h " + m + "m " + s + "s";
    }
}
