package com.kiva.arearollback;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class DeleteDirectory {
    // From:
    // https://www.baeldung.com/java-delete-directory
    // https://stackoverflow.com/a/46983076
    public static void deleteDirectory(Path path) throws IOException {
        if (!path.toFile().exists())
            return;

        Stream<Path> pathStream = Files.walk(path);
        pathStream.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    public static void deleteFilesInDirectory(Path path) throws IOException {
        if (!path.toFile().exists())
            return;

        File[] files = path.toFile().listFiles();
        if (files == null)
            return;

        for (File f : files)
            f.delete();
    }
}
