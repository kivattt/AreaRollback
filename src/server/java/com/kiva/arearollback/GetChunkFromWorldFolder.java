package com.kiva.arearollback;

import com.fox2code.foxloader.loader.ServerMod;
import net.minecraft.src.game.block.tileentity.TileEntity;
import net.minecraft.src.game.level.NibbleArray;
import net.minecraft.src.game.level.chunk.Chunk;
import net.minecraft.src.game.level.chunk.ThreegionFile;
import net.minecraft.src.game.nbt.NBTTagCompound;
import net.minecraft.src.game.nbt.NBTTagList;
import net.minecraft.src.server.CompressedStreamTools;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/*
  A lot of code copied from ReIndev, since it is so coupled
  I had to copy and edit it to be able to read region files
  from a different folder than the current server.
*/
public class GetChunkFromWorldFolder {
    private static final int CHUNK_SIZE = 4096;
    private static final int CHUNK_SIZE_DATA = CHUNK_SIZE / 2;
    private static final int CHUNK_SIZE_2D = 256;
    private static final byte[] chunk2DArray = new byte[CHUNK_SIZE_2D];
    private static final byte[] chunkSizedArray = new byte[CHUNK_SIZE];
    static final byte[] chunkDataArray = new byte[CHUNK_SIZE_DATA];

    public LinkedHashMap<File, SoftReference<ThreegionFile>> cache = new LinkedHashMap<>();
    public String zipFileRegionDir = null;

    // Takes in a chunk XYZ
    public Chunk getChunkFromRegionFolder(int dimension, boolean fromSelf, File backupPath, boolean isZipFile, int x, int y, int z) {
        ThreegionFile threegion;
        if (fromSelf)
            threegion = getFileForXYZFromSelf(dimension, x, y, z);
        else
            threegion = getFileForXYZ(dimension, backupPath, isZipFile, x, y, z);

        if (threegion == null)
            return null;

        DataInputStream distream = threegion.getChunkDataInputStream(x & 7, y & 7, z & 7);
        if (distream == null)
            return null;

        NBTTagCompound nbt;
        try {
            nbt = CompressedStreamTools.readNBTFromDataInput(distream);
        } catch (IOException e) {
            return null;
        }

        if (!nbt.hasKey("Level")) {
            ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Chunk file at " + x + "," + y + "," + z + " is missing level data");
            return null;
        }

        Chunk chunk = loadChunkFromCompound(nbt.getCompoundTag("Level"));
        if (!chunk.isAtLocation(x, y, z)) {
            ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Chunk file at " + x + "," + y + "," + z + " is in the wrong location; relocating. (Expected " + x + ", " + y + ", " + z + ", got " + chunk.xPosition + ", " + chunk.yPosition +", " + chunk.zPosition + ")");
            nbt.getCompoundTag("Level").setInteger("xPos", x);
            nbt.getCompoundTag("Level").setInteger("yPos", y);
            nbt.getCompoundTag("Level").setInteger("zPos", z);
            chunk = loadChunkFromCompound(nbt.getCompoundTag("Level"));
        }
        return chunk;
    }

    public String findRegionDirNameInZipFile(ZipFile zipFile, String regionDirName) {
        String entryNameClosestToRoot = null;

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (!e.isDirectory())
                continue;

            // Strip the last '/' so endsWith will match regionDirName
            String entryName = e.getName();
            if (entryName.charAt(entryName.length() - 1) == '/')
                entryName = entryName.substring(0, entryName.length() - 1);

            if (entryName.endsWith(regionDirName)) {
                if (entryNameClosestToRoot == null)
                    entryNameClosestToRoot = entryName;

                // We want the closest match to the zip root, so we don't get false-positives for server folders if they're inside the zipFile
                if (entryName.chars().filter(c -> c == '/').count() < entryNameClosestToRoot.chars().filter(c -> c == '/').count())
                    entryNameClosestToRoot = entryName;
            }
        }

        return entryNameClosestToRoot;
    }

    public ThreegionFile getFileForXYZ(int dimension, File backupPath, boolean isZipFile, int x, int y, int z) {
        String regionDirName = "world" + (dimension != 0 ? "/DIM-1" : "") + "/region";
        String regionFileName = "r." + (x >> 3) + "." + (y >> 3) + "." + (z >> 3) + ".r3";

        File threegion;

        if (isZipFile) {
            ZipFile zipFile;
            try {
                zipFile = new ZipFile(backupPath);
            } catch (IOException e) {
                return null;
            }

            File tmpRegionFile = new File(AreaRollbackServer.config.temporaryDirForUnzippedFiles + "/" + regionFileName);

            if (!tmpRegionFile.exists()) {
                // Recursively search for the regionDirName, since it could have parent folders if the user zipped a folder
                zipFileRegionDir = findRegionDirNameInZipFile(zipFile, regionDirName);

                // TODO: Region directory not found in zipFile, this error should be reported to the player! But right here it would spam...
                if (zipFileRegionDir == null) {
                    //ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Could not find region directory " + regionDirName + "in " + zipFile.getName());
                    return null;
                }

                ZipEntry regionFileEntry = zipFile.getEntry(zipFileRegionDir + "/" + regionFileName);
                if (regionFileEntry == null)
                    return null;

                try {
                    // If we're getting region files (.r3) out of a .zip file, store them inside a temporary folder in our mods folder
                    // This is done because `new ThreegionFile( "some path" )` requires a file, and not a ZipEntry
                    InputStream is = zipFile.getInputStream(regionFileEntry);
                    Files.copy(is, tmpRegionFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    threegion = tmpRegionFile;
                    zipFile.close();
                } catch (IOException ee) {
                    ee.printStackTrace();
                    return null;
                }
            } else {
                threegion = tmpRegionFile;
            }
        } else if (backupPath.isDirectory()) {
            threegion = new File(backupPath.getAbsolutePath() + "/" + regionDirName + "/" + regionFileName);
        } else {
            return null; // Not a directory or .zip file
        }

        if (!threegion.exists())
            return null;

        Reference<ThreegionFile> reference = cache.get(threegion);

        ThreegionFile threegion2;
        if (reference != null) {
            threegion2 = reference.get();
            if (threegion2 != null)
                return threegion2;
        }

        threegion2 = new ThreegionFile(threegion);
        cache.put(threegion, new SoftReference<>(threegion2));
        return threegion2;
    }

    public ThreegionFile getFileForXYZFromSelf(int dimension, int x, int y, int z) {
        String regionDirName = "world" + (dimension != 0 ? "/DIM-1" : "") + "/region";
        String regionFileName = "r." + (x >> 3) + "." + (y >> 3) + "." + (z >> 3) + ".r3";

        File threegion = new File(regionDirName + "/" + regionFileName);
        if (!threegion.exists())
            return null;

        Reference<ThreegionFile> reference = cache.get(threegion);

        ThreegionFile threegion2;
        if (reference != null) {
            threegion2 = reference.get();
            if (threegion2 != null)
                return threegion2;
        }

        threegion2 = new ThreegionFile(threegion);
        cache.put(threegion, new SoftReference<>(threegion2));
        return threegion2;
    }

    public Chunk loadChunkFromCompound(NBTTagCompound nbt) {
        int x = nbt.getInteger("xPos");
        int y = nbt.getInteger("yPos");
        int z = nbt.getInteger("zPos");
        Chunk chunk = new Chunk(null, x, y, z);
        byte[] blocks=decodeCompatByteArray(nbt.getByteArray("Blocks"), chunkSizedArray, false);
        byte[] blocks2=decodeCompatByteArray(nbt.getByteArray("Blocks2"), chunkSizedArray, false);
        //var4.blocks = nbt.getByteArray("Blocks");
        chunk.blocks=new short[blocks.length];
        for (int i=0;i<blocks.length;i++) {
            chunk.blocks[i]=(short)((blocks[i]&255)+((blocks2[i]&255)<<8));
        }
        chunk.blockBiomeArray=decodeCompatByteArray(nbt.getByteArray("Biomes"), chunk2DArray, true);
        if (chunk.blockBiomeArray == null || chunk.blockBiomeArray.length < 256) {
            chunk.blockBiomeArray = new byte[256];
        }
        chunk.data = decodeCompatNibbleArray(nbt.getByteArray("Data"));
        chunk.shadowMap = decodeCompatNibbleArray(nbt.getByteArray("SkyShadow"));
        chunk.blocklightMap = decodeCompatNibbleArray(nbt.getByteArray("BlockLight"));
        chunk.isTerrainPopulated = nbt.getBoolean("TerrainPopulated");
        if (!chunk.data.isValid()) {
            chunk.data = new NibbleArray();
        }

        // Not required for rollbacks
        /*if (!chunk.shadowMap.isValid()) {
            chunk.shadowMap = new NibbleArray();
        }

        if (!chunk.blocklightMap.isValid()) {
            chunk.blocklightMap = new NibbleArray();
            //chunk.func_1014_a();
        }

        NBTTagList nbttaglist = nbt.getTagList("Entities");
        if (nbttaglist != null) {
            for(int nbtiter = 0; nbtiter < nbttaglist.tagCount(); ++nbtiter) {
                NBTTagCompound enbt = (NBTTagCompound)nbttaglist.tagAt(nbtiter);
                Entity entity = EntityList.createEntityFromNBT(enbt, world);
                chunk.hasEntities = true;
                if (entity != null) {
                    chunk.addEntity(entity);
                }
            }
        }*/

        NBTTagList tileentities = nbt.getTagList("TileEntities");
        if (tileentities != null) {
            for(int var11 = 0; var11 < tileentities.tagCount(); ++var11) {
                NBTTagCompound var12 = (NBTTagCompound)tileentities.tagAt(var11);
                TileEntity var9 = TileEntity.createAndLoadEntity(var12);
                if (var9 != null) {
                    chunk.addTileEntity(var9);
                }
            }
        }

        return chunk;
    }

    private byte[] decodeCompatByteArray(byte[] array, byte[] emptyArray, boolean dup) {
        int len = array.length;
        if (len > 2) return array;
        switch (len) {
            case 0:
                return dup ? Arrays.copyOf(emptyArray, emptyArray.length) : emptyArray;
            case 1:
                byte component = array[0];
                array = Arrays.copyOf(emptyArray, emptyArray.length);
                Arrays.fill(array, component);
                return array;
            default:
                throw new IllegalArgumentException(
                        "Incompatible compat byte format length, got " +
                                len + ", expected: [0, 1, " + emptyArray.length + "]");
        }
    }

    private NibbleArray decodeCompatNibbleArray(byte[] array) {
        byte[] decodedByteArray = decodeCompatByteArray(array, chunkDataArray, true);
        if (decodedByteArray.length != CHUNK_SIZE_DATA) {
            return new NibbleArray(CHUNK_SIZE);
        }
        return new NibbleArray(decodedByteArray);
    }
}
