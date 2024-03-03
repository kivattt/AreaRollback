package com.kiva.arearollback;

import java.util.List;

public class BackupFilenames {
    public List<String> folders;
    public List<String> zipFiles;

    public BackupFilenames(List<String> folders, List<String> zipFiles){
        this.folders = folders;
        this.zipFiles = zipFiles;
    }
}
