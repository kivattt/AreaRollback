package com.kiva.arearollback;

import com.fox2code.foxloader.loader.ServerMod;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.commands.Rollback;
import com.kiva.arearollback.commands.RollbackFlipDimension;
import com.kiva.arearollback.commands.RollbackFromSelf;
import com.kiva.arearollback.commands.RollbackVersion;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AreaRollbackServer extends AreaRollback implements ServerMod {
    public static final String areaRollbackBasePath = "mods/AreaRollback";
    public static final String configFilename = areaRollbackBasePath + "/config.txt";
    public static Config config = new Config();
    public static boolean configLoadedSuccessfully = false;
    public static boolean flipDimensionForRollbacks = false; // Toggled by the RollbackFlipDimension.java command

    @Override
    public void onInit() {
        configLoadedSuccessfully = config.loadFromFile(configFilename);

        CommandCompat.registerCommand(new Rollback());
        CommandCompat.registerCommand(new RollbackFlipDimension());
        CommandCompat.registerCommand(new RollbackFromSelf());
        CommandCompat.registerCommand(new RollbackVersion());

        System.out.println("AreaRollback initialized");
    }

    @Override
    public void onServerStop(NetworkPlayer.ConnectionType connectionType) {
        // Make sure mods/AreaRollback directory exists
        Path modsPath = Paths.get(areaRollbackBasePath);
        try {
            Files.createDirectory(modsPath);
        } catch (IOException e) {
            if (!(e instanceof FileAlreadyExistsException)) {
                ServerMod.getGameInstance().logWarning(AreaRollback.loggingPrefix + "Unable to create directory: " + areaRollbackBasePath);
                e.printStackTrace();
            }
        }

        File f = new File(configFilename);
        if (f.exists())
            return;

        try {
            FileWriter fileWriter = new FileWriter(configFilename);
            fileWriter.write("# Default empty config file generated by AreaRollbackServer\n");
            fileWriter.write("\n");
            fileWriter.write("# REQUIRED! The folder containing all your backups as folders and/or .zip files\n");
            fileWriter.write(config.backupsDirKeyName + "=\n");
            fileWriter.write("\n");
            fileWriter.write("# Only change this if you're really low on disk space and have another drive\n");
            fileWriter.write("# Since it is a temporary folder, it will be deleted after using /rollback, so don't be alarmed if it's suddenly missing\n");
            fileWriter.write("# When left empty or missing, the default is mods/AreaRollback/tmp-dont-delete\n");
            fileWriter.write(config.temporaryDirForUnzippedFilesKeyName + "=\n");
            fileWriter.close();
        } catch (IOException e) {
            ServerMod.getGameInstance().logWarning(loggingPrefix + "Failed to write empty default config to " + configFilename);
        }
    }
}
