package com.kiva.arearollback.commands;

import com.fox2code.foxloader.loader.ServerMod;
import com.fox2code.foxloader.network.ChatColors;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.AreaRollbackServer;
import com.kiva.arearollback.GetChunkFromWorldFolder;
import net.minecraft.src.game.block.tileentity.TileEntity;
import net.minecraft.src.game.level.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

public class RollbackFromSelf extends CommandCompat {
    public RollbackFromSelf() {
        super("rollbackfromself", true);
    }

    @Override
    public String commandSyntax() {
        return ChatColors.YELLOW + "/rollbackfromself";
    }

    @Override
    public void onExecute(String[] args, NetworkPlayer commandExecutor) {
        NetworkPlayer.NetworkPlayerController netPlayerController = commandExecutor.getNetworkPlayerController();

        if (!netPlayerController.hasSelection()){
            commandExecutor.displayChatMessage(ChatColors.RED + "No area selected!");
            commandExecutor.displayChatMessage(ChatColors.RED + "Mark the corners of the area with");
            commandExecutor.displayChatMessage(ChatColors.RED + "a wooden axe in creative mode as OP");
            return;
        }

        ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + commandExecutor.getPlayerName() + ": Rolling back" + (AreaRollbackServer.flipDimensionForRollbacks ? " (dimension flipped)" : "") + ", this halts the server until finished");
        commandExecutor.displayChatMessage(ChatColors.GREEN + "Rolling back " + (AreaRollbackServer.flipDimensionForRollbacks ? (ChatColors.RED + "(dimension flipped) " + ChatColors.GREEN) : "") + "...");

        long start = System.currentTimeMillis();
        boolean backupMissingSomeChunk;
        try {
            backupMissingSomeChunk = doRollbackFromSelf(netPlayerController, ServerMod.toEntityPlayerMP(commandExecutor).dimension);
        } catch (IOException e) {
            commandExecutor.displayChatMessage(ChatColors.RED + "Unknown file IO error during rollback, check console");
            e.printStackTrace();
            return;
        }

        long end = System.currentTimeMillis();
        long duration = end - start;

        ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + commandExecutor.getPlayerName() + ": Rollback " + (AreaRollbackServer.flipDimensionForRollbacks ? "(dimension flipped) " : "") + "performed in " + duration + " milliseconds");
        commandExecutor.displayChatMessage(ChatColors.GREEN + "Rollback " + (AreaRollbackServer.flipDimensionForRollbacks ? (ChatColors.RED + "(dimension flipped) " + ChatColors.GREEN) : "") + "performed in " + ChatColors.RESET + duration + ChatColors.GREEN + " milliseconds");

        if (backupMissingSomeChunk) {
            ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + commandExecutor.getPlayerName() + ": Some chunks not found were untouched");
            commandExecutor.displayChatMessage(ChatColors.RED + "Some chunks not found were untouched");
        }

        commandExecutor.displayChatMessage(ChatColors.YELLOW + "Re-join the server to see the changes!");
    }

    public static boolean doRollbackFromSelf(NetworkPlayer.NetworkPlayerController npc, int dimension) throws IOException {
        GetChunkFromWorldFolder getChunkFromWorldFolder = new GetChunkFromWorldFolder();

        boolean backupMissingSomeChunk = false;

        // TODO: If we only get a new chunk when necessary in this loop, we don't have to cache them in getChunkFromRegionFolder()
        for (int x = npc.getMinX(); x <= npc.getMaxX(); x++) {
            for (int y = npc.getMinY(); y <= npc.getMaxY(); y++) {
                for (int z = npc.getMinZ(); z <= npc.getMaxZ(); z++) {
                    int dimensionToCopyFrom = dimension;
                    if (AreaRollbackServer.flipDimensionForRollbacks)
                        dimensionToCopyFrom = dimension == 0 ? -1 : 0;

                    Chunk chunk = getChunkFromWorldFolder.getChunkFromRegionFolder(dimensionToCopyFrom, true, new File(""), false, x >> 4, y >> 4, z >> 4);

                    if (chunk == null) {
                        backupMissingSomeChunk = true;
                        continue;
                    }

                    int blockID = chunk.getBlockId(x & 15, y & 15, z & 15);
                    int metadata = chunk.getBlockMetadata(x & 15, y & 15, z & 15);
                    ServerMod.getGameInstance().getWorldManager(dimension).setBlockAndMetadata(x, y, z, blockID, metadata);

                    TileEntity tileEntity = chunk.getChunkBlockTileEntity(x & 15, y & 15, z & 15);
                    if (tileEntity != null)
                        ServerMod.getGameInstance().getWorldManager(dimension).setBlockTileEntity(x, y, z, tileEntity);
                }
            }
        }

        getChunkFromWorldFolder.cache = new LinkedHashMap<>();

        return backupMissingSomeChunk;
    }
}
