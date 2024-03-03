package com.kiva.arearollback.commands;

import com.fox2code.foxloader.loader.ServerMod;
import com.fox2code.foxloader.network.ChatColors;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.AreaRollbackServer;
import com.kiva.arearollback.BackupFilenames;
import com.kiva.arearollback.GetChunkFromWorldFolder;
import net.minecraft.src.game.block.tileentity.TileEntity;
import net.minecraft.src.game.level.chunk.Chunk;

import java.io.File;
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

        if (!netPlayerController.hasSelection()){
            commandExecutor.displayChatMessage(ChatColors.RED + "No area selected!");
            commandExecutor.displayChatMessage(ChatColors.RED + "Mark the corners of the area with");
            commandExecutor.displayChatMessage(ChatColors.RED + "a wooden axe in creative mode as OP");
            return;
        }

        BackupFilenames backups = getBackupsNames();
        if (backups.folders.isEmpty() && backups.zipFiles.isEmpty()) {
            commandExecutor.displayChatMessage(ChatColors.RED + "No backups available!");
            commandExecutor.displayChatMessage(ChatColors.YELLOW + "Did you set backups-dir correctly in config.txt?");
            return;
        }

        if (args.length == 1) {
            commandExecutor.displayChatMessage(ChatColors.AQUA + "Backups available:");
            for (String folder : backups.folders)
                commandExecutor.displayChatMessage(folder);

            for (String zipFile : backups.zipFiles)
                commandExecutor.displayChatMessage(ChatColors.DARK_RED + zipFile);

            commandExecutor.displayChatMessage(ChatColors.GREEN + "Type /rollback <backup> to roll back!");

            return;
        }

        if (args.length == 2) {
            String backupSelected = args[1];

            if (!backups.folders.contains(backupSelected) && !backups.zipFiles.contains(backupSelected)) {
                commandExecutor.displayChatMessage(ChatColors.RED + "That backup does not exist!");
                commandExecutor.displayChatMessage(ChatColors.YELLOW + "Type /rollback to list available backups");
                return;
            }


            long start = System.currentTimeMillis();
            boolean backupMissingSomeChunk = doRollback(backupSelected, netPlayerController, ServerMod.toEntityPlayerMP(commandExecutor).dimension);
            long end = System.currentTimeMillis();
            long duration = end - start;

            if (backupMissingSomeChunk)
                commandExecutor.displayChatMessage(ChatColors.YELLOW + "Some chunks not found in the backup were untouched");

            commandExecutor.displayChatMessage(ChatColors.GREEN + "Rollback performed in " + ChatColors.RESET + duration + ChatColors.GREEN + " milliseconds");

            return;
        }

        commandExecutor.displayChatMessage(commandSyntax());
    }

    // Returns: Folders       Zip files
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

    // Probably really slow, should pre-load all the chunks needed then setblock based on indexing them instead of this
    boolean doRollback(String backupSelected, NetworkPlayer.NetworkPlayerController npc, int dimension) {
        GetChunkFromWorldFolder getChunkFromWorldFolder = new GetChunkFromWorldFolder();

        Path regionDir = Paths.get(AreaRollbackServer.config.backupsDir + "/" + backupSelected);

        boolean backupMissingSomeChunk = false;

        for (int x = npc.getMinX(); x <= npc.getMaxX(); x++) {
            for (int y = npc.getMinY(); y <= npc.getMaxY(); y++) {
                for (int z = npc.getMinZ(); z <= npc.getMaxZ(); z++) {
                    Chunk chunk = getChunkFromWorldFolder.getChunkFromRegionFolder(dimension, regionDir.normalize().toFile(), x >> 4, y >> 4, z >> 4);

                    if (chunk == null) {
                        backupMissingSomeChunk = true;
                        continue;
                    }

                    //ServerMod.getGameInstance().getWorldManager(dimension).setBlockWithNotify(x, y, z, chunk.getBlockId(x & 15, y & 15, z & 15));
                    if (chunk.blocks == null)
                        System.out.println("OH NO!! blocks = null");

                    int blockID = chunk.getBlockId(x & 15, y & 15, z & 15);
                    int metadata = chunk.getBlockMetadata(x & 15, y & 15, z & 15);
                    ServerMod.getGameInstance().getWorldManager(dimension).setBlockAndMetadataWithNotify(x, y, z, blockID, metadata);

                    TileEntity tileEntity = chunk.getChunkBlockTileEntity(x & 15, y & 15, z & 15);
                    if (tileEntity != null)
                        ServerMod.getGameInstance().getWorldManager(dimension).setBlockTileEntity(x, y, z, tileEntity);

                    //ServerMod.getGameInstance().getWorldManager(dimension).addLoadedTileEntities(chunk.worldObj.loadedTileEntityList);
                }
            }
        }

        //
        getChunkFromWorldFolder.cache = new LinkedHashMap<>();
        getChunkFromWorldFolder.zipFileRegionDir = null;

        return backupMissingSomeChunk;
    }
}
