package com.kiva.arearollback;

import com.fox2code.foxloader.loader.ServerMod;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Config {
    public static final String backupsDirKeyName = "backups-dir";
    public String backupsDir = null;

    public static final String temporaryDirForUnzippedFilesKeyName = "temporary-dir";
    public String temporaryDirForUnzippedFiles = null;

    public boolean loadFromFile(final String filename) {
        Scanner scanner;
        try{
            scanner = new Scanner(new File(filename));
        } catch (FileNotFoundException e) {
            ServerMod.getGameInstance().logWarning(AreaRollback.loggingPrefix + "Failed to read config file " + filename + " (an empty one will be generated on server stop)");
            return false;
        }

        int lineNum = 0;
        while (scanner.hasNextLine()) {
            ++lineNum;
            String line = scanner.nextLine();

            if (line.startsWith("#") || line.isEmpty())
                continue;

            int separatorIndex = line.indexOf("=");
            if (separatorIndex == -1) {
                ServerMod.getGameInstance().logWarning(AreaRollback.loggingPrefix + "Failed to find '=' separator on line " + lineNum + " in config file " + filename);
                continue;
            }

            String value = line.substring(separatorIndex + 1).trim();

            if (line.startsWith(backupsDirKeyName))
                backupsDir = value;
            else if (line.startsWith(temporaryDirForUnzippedFilesKeyName))
                temporaryDirForUnzippedFiles = value;
        }

        if (backupsDir == null || backupsDir.isEmpty()) {
            ServerMod.getGameInstance().logWarning(AreaRollback.loggingPrefix + "Config key `" + backupsDirKeyName + "` missing, or empty!");
            return false;
        }

        File file = new File(backupsDir);
        if (!file.isAbsolute()) {
            ServerMod.getGameInstance().logWarning(AreaRollback.loggingPrefix + "Config key `" + backupsDirKeyName + "` is a relative path, it has to be absolute!");
            return false;
        }

        if (temporaryDirForUnzippedFiles == null || temporaryDirForUnzippedFiles.isEmpty()) {
            temporaryDirForUnzippedFiles = AreaRollbackServer.areaRollbackBasePath + "/tmp-dont-delete";
        } else {
            file = new File(temporaryDirForUnzippedFiles);
            if (!file.isAbsolute()) {
                ServerMod.getGameInstance().logWarning(AreaRollback.loggingPrefix + "Config key `" + temporaryDirForUnzippedFiles + "` is a relative path, it has to be absolute!");
                return false;
            }
        }

        return true;
    }
}
