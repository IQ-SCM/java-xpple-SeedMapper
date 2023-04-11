package dev.xpple.seedmapper.command.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mcterrain.TerrainGenerator;
import dev.xpple.seedmapper.command.ClientCommand;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.command.SharedHelpers;
import dev.xpple.seedmapper.simulation.SimulatedServer;
import dev.xpple.seedmapper.simulation.SimulatedWorld;
import dev.xpple.seedmapper.util.config.Configs;
import dev.xpple.seedmapper.util.maps.SimpleBlockMap;
import dev.xpple.seedmapper.util.render.RenderQueue;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

import static dev.xpple.seedmapper.util.ChatBuilder.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SeedOverlayCommand extends ClientCommand implements SharedHelpers.Exceptions {

    @Override
    protected LiteralCommandNode<FabricClientCommandSource> build(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        return dispatcher.register(literal(this.getRootLiteral())
            .executes(ctx -> seedOverlay(CustomClientCommandSource.of(ctx.getSource()))));
    }

    @Override
    protected String rootLiteral() {
        return "seedoverlay";
    }

    @Override
    protected String alias() {
        return "overlay";
    }

    private static int seedOverlay(CustomClientCommandSource source) throws CommandSyntaxException {
        Map<Box, Block> boxes;

        if (Configs.UseWorldSimulation) {
            boxes = overlayUsingWorldSimulation(source);
        } else {
            boxes = overlayUsingLibraries(source);
        }

        boxes.forEach((key, value) -> {
            RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, key, key, Configs.BlockColours.get(value),  30 * 20);

            double x, y, z;
            x = key.minX;
            y = key.minY;
            z = key.minZ;
            String terrainBlockName = Registries.BLOCK.getId(value).getPath();
            source.sendFeedback(chain(
                highlight(Text.translatable("command.seedoverlay.feedback.blockAt")),
                copy(
                    hover(
                        accent("x: " + x + ", y: " + y + ", z: " + z),
                        chain(
                            base(Text.translatable("command.seedoverlay.feedback.copy")),
                            highlight(terrainBlockName)
                        )
                    ),
                    String.format("%.0f %.0f %.0f", x, y ,z)
                ),
                highlight(Text.translatable("command.seedoverlay.feedback.noMatch"))
            ));
        });

        int blocks = boxes.size();
        if (blocks > 0) {
            source.sendFeedback(chain(
                highlight(Text.translatable("command.seedoverlay.feedback.total")),
                accent(String.valueOf(blocks)),
                highlight(Text.translatable("command.seedoverlay.feedback.dontMatch"))
            ));
        } else {
            source.sendFeedback(highlight(Text.translatable("command.seedoverlay.feedback.allMatch")));
        }
        return blocks;
    }

    private static Map<Box, Block> overlayUsingWorldSimulation(CustomClientCommandSource source) throws CommandSyntaxException {
        SharedHelpers helpers = new SharedHelpers(source);

        if (!helpers.mcVersion().name.equals(SharedConstants.getGameVersion().getName())) {
            throw UNSUPPORTED_VERSION_EXCEPTION.create();
        }

        try (SimulatedServer server = SimulateCommand.currentServer == null ? SimulatedServer.newServer(helpers.seed()) : SimulateCommand.currentServer) {
            SimulatedWorld world = SimulateCommand.currentWorld == null ? new SimulatedWorld(server, helpers.dimension()) : SimulateCommand.currentWorld;
            BlockPos blockPos = BlockPos.ofFloored(source.getPosition());

            return overlayUsingWorldSimulation(source.getWorld().getChunk(blockPos), world.getChunk(blockPos));
        }
    }

    public static Map<Box, Block> overlayUsingWorldSimulation(Chunk gameChunk, Chunk simulatedChunk) {
        Map<Box, Block> boxes = new HashMap<>();

        ChunkPos chunkPos = gameChunk.getPos();
        final int startX, endX, startY, endY, startZ, endZ;
        startX = chunkPos.getStartX();
        endX = chunkPos.getEndX();
        startY = gameChunk.getBottomY();
        endY = gameChunk.getTopY();
        startZ = chunkPos.getStartZ();
        endZ = chunkPos.getEndZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = startX; x <= endX; x++) {
            mutable.setX(x);
            for (int z = startZ; z <= endZ; z++) {
                mutable.setZ(z);
                for (int y = startY; y <= endY; y++) {
                    mutable.setY(y);
                    Block gameBlock = gameChunk.getBlockState(mutable).getBlock();
                    if (Configs.IgnoredBlocks.contains(gameBlock)) {
                        continue;
                    }
                    if (simulatedChunk.getBlockState(mutable).isOf(gameBlock)) {
                        continue;
                    }
                    boxes.put(new Box(mutable), gameBlock);
                }
            }
        }

        return boxes;
    }

    private static Map<Box, Block> overlayUsingLibraries(CustomClientCommandSource source) throws CommandSyntaxException {
        SharedHelpers helpers = new SharedHelpers(source);

        BiomeSource biomeSource = BiomeSource.of(helpers.dimension(), helpers.mcVersion(), helpers.seed());
        TerrainGenerator terrainGenerator = TerrainGenerator.of(biomeSource);

        BlockPos blockPos = BlockPos.ofFloored(source.getPosition());
        Chunk gameChunk = source.getWorld().getChunk(blockPos);

        return overlayUsingLibraries(gameChunk, terrainGenerator);
    }

    public static Map<Box, Block> overlayUsingLibraries(Chunk gameChunk, TerrainGenerator terrainGenerator) {
        Map<Box, Block> boxes = new HashMap<>();

        final SimpleBlockMap map = new SimpleBlockMap(terrainGenerator.getVersion(), terrainGenerator.getDimension(), Biomes.PLAINS);
        BiomeSource biomeSource = terrainGenerator.getBiomeSource();

        ChunkPos chunkPos = gameChunk.getPos();
        final int startX, endX, startZ, endZ;
        startX = chunkPos.getStartX();
        endX = chunkPos.getEndX();
        startZ = chunkPos.getStartZ();
        endZ = chunkPos.getEndZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = startX; x <= endX; x++) {
            mutable.setX(x);
            for (int z = startZ; z <= endZ; z++) {
                mutable.setZ(z);
                var column = terrainGenerator.getColumnAt(x, z);
                Biome biome = biomeSource.getBiome(x, 0, z);
                map.setBiome(biome);
                for (int y = 0; y < column.length; y++) {
                    mutable.setY(y);
                    Block gameBlock = gameChunk.getBlockState(mutable).getBlock();
                    if (Configs.IgnoredBlocks.contains(gameBlock)) {
                        continue;
                    }
                    if (map.get(gameBlock) == column[y].getId()) {
                        continue;
                    }
                    boxes.put(new Box(mutable), gameBlock);
                }
            }
        }

        return boxes;
    }
}
