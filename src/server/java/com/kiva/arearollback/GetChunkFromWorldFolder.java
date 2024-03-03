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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    public Chunk getChunkFromRegionFolder(int dimension, File backupPath, int x, int y, int z) {
        ThreegionFile threegion = getFileForXYZ(dimension, backupPath, x, y, z);
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
            //ServerMod.getGameInstance().logWarning(AreaRollbackServer.loggingPrefix + "Chunk file at " + x + "," +y+"," + z + " is in the wrong location; relocating. (Expected " + x + ", " +y+", " + z + ", got " + chunk.xPosition + ", " + chunk.yPosition +", " + chunk.zPosition + ")");
            nbt.getCompoundTag("Level").setInteger("xPos", x);
            nbt.getCompoundTag("Level").setInteger("yPos", y);
            nbt.getCompoundTag("Level").setInteger("zPos", z);
            chunk = loadChunkFromCompound(nbt.getCompoundTag("Level"));
        }
        return chunk;
    }

    public String findRegionDirNameInZipFile(ZipFile zipFile, String regionDirName) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (!e.isDirectory())
                continue;

            // Strip the last '/' so endsWith will match regionDirName
            String entryName = e.getName();
            if (entryName.charAt(entryName.length() - 1) == '/')
                entryName = entryName.substring(0, entryName.length() - 1);

            if (entryName.endsWith(regionDirName))
                return entryName;
        }

        return null;
    }

    public ThreegionFile getFileForXYZ(int dimension, File backupPath, int x, int y, int z) {
        String regionDirName = "world" + (dimension != 0 ? "/DIM-1" : "") + "/region";
        String regionFileName = "r." + (x >> 3) + "." + (y >> 3) + "." + (z >> 3) + ".r3";

        File threegion;

        boolean isZipFile = backupPath.isFile() && backupPath.getName().endsWith(".zip");
        if (isZipFile){
            ZipFile zipFile;
            try {
                zipFile = new ZipFile(backupPath);
            } catch (IOException e) {
                return null;
            }

            // Recursively search for the regionDirName, since it could have parent folders if the user zipped a folder
            //if (zipFileRegionDir == null) {
            zipFileRegionDir = findRegionDirNameInZipFile(zipFile, regionDirName);
//                System.out.println("Found region dir in zip file: " + zipFileRegionDir);
            //}
            //ZipEntry e = zipFile.getEntry(regionDirName + "/" + regionFileName);
            ZipEntry regionFileEntry = zipFile.getEntry(zipFileRegionDir + "/" + regionFileName);
            try {
                // If we're getting a region file (.r3) out of a .zip file, store it in a temporary file in our mods folder
                // This is done because `new ThreegionFile( "some path" )` requires a file, and not a ZipEntry
                //System.out.println("copying region file: " + regionFileEntry.getName());
                InputStream is = zipFile.getInputStream(regionFileEntry);
                //System.out.println("Replacing tmp with: " + regionFileEntry.getName());
                String tmpRegionFile = AreaRollbackServer.areaRollbackBasePath + "/tmp-do-not-delete.r3"; // TODO: Delete this when done
                Files.copy(is, Paths.get(tmpRegionFile), StandardCopyOption.REPLACE_EXISTING);

                threegion = new File(tmpRegionFile);
                zipFile.close();

                // No cache for zip at the moment
                return new ThreegionFile(threegion);
            } catch (IOException ee) {
                ee.printStackTrace();
                return null;
            }
        } else if (backupPath.isDirectory()) {
            threegion = new File(backupPath.getAbsolutePath() + "/" + regionDirName + "/" + regionFileName);
        } else {
            return null; // Not a directory or .zip file
        }

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
