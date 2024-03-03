package com.kiva.arearollback;

import com.fox2code.foxloader.loader.ServerMod;
import com.fox2code.foxloader.network.NetworkPlayer;
import com.fox2code.foxloader.registry.CommandCompat;
import com.kiva.arearollback.commands.Rollback;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AreaRollbackServer extends AreaRollback implements ServerMod {
    public static final String areaRollbackBasePath = "mods/AreaRollback/";
    public static final String configFilename = areaRollbackBasePath + "config.txt";
    public static Config config = new Config();

    @Override
    public void onInit() {
        config.loadFromFile(configFilename);

        CommandCompat.registerCommand(new Rollback());

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
                return;
            }
        }
    }
}
