package com.kiva.arearollback.commands;

import com.fox2code.foxloader.loader.ServerMod;
import com.fox2code.foxloader.network.ChatColors;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.*;
import net.minecraft.src.game.block.tileentity.TileEntity;
import net.minecraft.src.game.level.chunk.Chunk;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class Rollback extends CommandCompat {
    public Rollback(){
        super("rollback", true);
    }

    public String commandSyntax() {
        return ChatColors.YELLOW + "/rollback <optional backup name>";
    }

    @Override
    public void onExecute(String[] args, NetworkPlayer commandExecutor) {
        NetworkPlayer.NetworkPlayerController netPlayerController = commandExecutor.getNetworkPlayerController();

        if (!AreaRollbackServer.configLoadedSuccessfully) {
            commandExecutor.displayChatMessage(ChatColors.RED + "Invalid config, check server console");
            return;
        }

        if (!netPlayerController.hasSelection()){
            commandExecutor.displayChatMessage(ChatColors.RED + "No area selected!");
            commandExecutor.displayChatMessage(ChatColors.RED + "Mark the corners of the area with");
            commandExecutor.displayChatMessage(ChatColors.RED + "a wooden axe in creative mode as OP");
            return;
        }

        BackupFilenames backups = getBackupsNames();
        if (backups.folders.isEmpty() && backups.zipFiles.isEmpty()) {
            commandExecutor.displayChatMessage(ChatColors.RED + "No backups available!");
            commandExecutor.displayChatMessage(ChatColors.YELLOW + "Did you set " + Config.backupsDirKeyName + " correctly in config.txt?");
            return;
        }

        if (args.length == 1) {
            commandExecutor.displayChatMessage(ChatColors.AQUA + "Backups available:");
            for (String folder : backups.folders)
                commandExecutor.displayChatMessage(folder);

            for (String zipFile : backups.zipFiles)
                commandExecutor.displayChatMessage(ChatColors.BLUE + zipFile);

            commandExecutor.displayChatMessage(ChatColors.GREEN + "Type /rollback <backup> to roll back");

            return;
        }

        if (args.length == 2) {
            String backupSelected = args[1];
            if (backupSelected.isEmpty()) {
                commandExecutor.displayChatMessage(ChatColors.RED + "You can't use an empty backup name");
                commandExecutor.displayChatMessage(ChatColors.RED + "(You probably typed 2 spaces in the command)");
                return;
            }

            if (!backups.folders.contains(backupSelected) && !backups.zipFiles.contains(backupSelected)) {
                commandExecutor.displayChatMessage(ChatColors.RED + "That backup does not exist!");
                commandExecutor.displayChatMessage(ChatColors.YELLOW + "Type /rollback to list available backups");
                return;
            }

            ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + commandExecutor.getPlayerName() + ": Rolling back" + (AreaRollbackServer.flipDimensionForRollbacks ? " (dimension flipped)" : "") + " this halts the server until finished");
            commandExecutor.displayChatMessage(ChatColors.GREEN + "Rolling back " + (AreaRollbackServer.flipDimensionForRollbacks ? (ChatColors.RED + "(dimension flipped) " + ChatColors.GREEN) : "") + "...");

            long start = System.currentTimeMillis();
            boolean backupMissingSomeChunk;
            try {
                backupMissingSomeChunk = doRollback(backupSelected, netPlayerController, ServerMod.toEntityPlayerMP(commandExecutor).dimension);
            } catch (IOException e) {
                commandExecutor.displayChatMessage(ChatColors.RED + "Failed to create or delete temporary folder for unzipping files");
                return;
            }
            long end = System.currentTimeMillis();
            long duration = end - start;

            ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + commandExecutor.getPlayerName() + ": Rollback " + (AreaRollbackServer.flipDimensionForRollbacks ? "(dimension flipped) " : "") + "performed in " + duration + " milliseconds");
            commandExecutor.displayChatMessage(ChatColors.GREEN + "Rollback " + (AreaRollbackServer.flipDimensionForRollbacks ? (ChatColors.RED + "(dimension flipped) " + ChatColors.GREEN) : "") + "performed in " + ChatColors.RESET + duration + ChatColors.GREEN + " milliseconds");

            if (backupMissingSomeChunk)
                commandExecutor.displayChatMessage(ChatColors.RED + "Some chunks not found in the backup were untouched");

            commandExecutor.displayChatMessage(ChatColors.YELLOW + "Re-join the server to see the changes!");

            return;
        }

        commandExecutor.displayChatMessage(commandSyntax());
    }

    public BackupFilenames getBackupsNames() {
        File file = new File(AreaRollbackServer.config.backupsDir);
        String[] folders = file.list((dir, name) -> new File(dir, name).isDirectory());

        String[] zipFiles = file.list((dir, name) -> {
            File f = new File(dir, name);
            return f.isFile() && f.getName().endsWith(".zip");
        });

        List<String> foldersList = new ArrayList<>();
        List<String> zipFilesList = new ArrayList<>();
        if (folders != null) foldersList = Arrays.asList(folders);
        if (zipFiles != null) zipFilesList = Arrays.asList(zipFiles);

        return new BackupFilenames(foldersList, zipFilesList);
    }

    public static boolean doRollback(String backupSelected, NetworkPlayer.NetworkPlayerController npc, int dimension) throws IOException {
        GetChunkFromWorldFolder getChunkFromWorldFolder = new GetChunkFromWorldFolder();

        Path regionDirPath = Paths.get(AreaRollbackServer.config.backupsDir + "/" + backupSelected);
        File regionDir = regionDirPath.normalize().toFile();

        Path tmpFolderName = Paths.get(AreaRollbackServer.config.temporaryDirForUnzippedFiles);

        boolean isZipFile = regionDir.isFile() && regionDir.getName().endsWith(".zip");
        if (isZipFile) {
            try {
                Files.createDirectory(tmpFolderName);
            } catch (IOException e) {
                if (e instanceof FileAlreadyExistsException) {
                    // This should only happen if the server was killed before we had the chance to delete the temp folder
                    try {
                        DeleteDirectory.deleteDirectory(tmpFolderName);
                    } catch(IOException ee) {
                        ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Failed to delete directory: " + tmpFolderName);
                        throw ee;
                    }
                } else {
                    ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Failed to create directory: " + tmpFolderName);
                    throw e;
                }
            }
        }

        boolean backupMissingSomeChunk = false;

        // TODO: If we only get a new chunk when necessary in this loop, we don't have to cache them in getChunkFromRegionFolder()
        for (int x = npc.getMinX(); x <= npc.getMaxX(); x++) {
            for (int y = npc.getMinY(); y <= npc.getMaxY(); y++) {
                for (int z = npc.getMinZ(); z <= npc.getMaxZ(); z++) {
                    int dimensionToCopyFrom = dimension;
                    if (AreaRollbackServer.flipDimensionForRollbacks)
                        dimensionToCopyFrom = dimension == 0 ? -1 : 0;

                    Chunk chunk = getChunkFromWorldFolder.getChunkFromRegionFolder(dimensionToCopyFrom, false, regionDir, isZipFile, x >> 4, y >> 4, z >> 4);

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
        getChunkFromWorldFolder.zipFileRegionDir = null;

        if (isZipFile) {
            try {
                DeleteDirectory.deleteDirectory(tmpFolderName);
            } catch (IOException ee) {
                ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Failed to delete directory: " + tmpFolderName);
                throw ee;
            }
        }

        return backupMissingSomeChunk;
    }
}
