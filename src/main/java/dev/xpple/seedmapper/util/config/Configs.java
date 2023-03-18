package dev.xpple.seedmapper.util.config;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seedfinding.mccore.state.Dimension;
import dev.xpple.betterconfig.api.Config;
import dev.xpple.seedmapper.command.SharedHelpers;
import dev.xpple.seedmapper.simulation.SimulatedServer;
import dev.xpple.seedmapper.simulation.SimulatedWorld;
import net.minecraft.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dev.xpple.seedmapper.SeedMapper.CLIENT;

@SuppressWarnings("unused")
public class Configs {
    @Config
    public static Long Seed = null;

    @Config(putter = @Config.Putter("none"), adder = @Config.Adder(value = "addSavedSeed", type = long.class))
    public static Map<String, Long> SavedSeeds = new HashMap<>();
    public static void addSavedSeed(long seed) {
        String key = CLIENT.getNetworkHandler().getConnection().getAddress().toString();
        SavedSeeds.put(key, seed);
    }

    @Config
    public static Set<Block> IgnoredBlocks = new HashSet<>();

    @Config(setter = @Config.Setter("setAutoOverlay"))
    public static boolean AutoOverlay = false;
    public static void setAutoOverlay(boolean autoOverlay) throws CommandSyntaxException {
        AutoOverlay = autoOverlay;
        if (autoOverlay) {
            long seed = SharedHelpers.getSeed(null);
            try {
                SimulatedServer.currentInstance = SimulatedServer.newServer(seed);
            } catch (Exception e) {
                if (e instanceof CommandSyntaxException commandSyntaxException) {
                    throw commandSyntaxException;
                }
                throw SharedHelpers.Exceptions.WORLD_SIMULATION_ERROR_EXCEPTION.create(e.getMessage());
            }
            Dimension dimension = Dimension.fromString(CLIENT.world.getRegistryKey().getValue().getPath());
            if (dimension == null) {
                return;
            }
            SimulatedWorld.currentInstance = new SimulatedWorld(SimulatedServer.currentInstance, dimension);
        } else {
            SimulatedServer.currentInstance = null;
            SimulatedWorld.currentInstance = null;
        }
    }

    @Config
    public static Map<Block, Integer> BlockColours = new HashMap<>();

    @Config
    public static SeedResolution SeedResolutionOrder = new SeedResolution();

    @Config
    public static boolean UseWorldSimulation = false;
}
