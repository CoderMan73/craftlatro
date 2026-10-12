package com.craftlatro.client.extractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class LoveExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    /**
     * Balatro.exe is a LOVE game archive packaged as a ZIP file.
     * The ZIP starts with PK\x03\x04 (0x504B0304) magic bytes, which may appear at an offset.
     *
     * @param balatroExe   Path to the user's Balatro.exe
     * @param outputLove   Output path for the .love file
     * @return true if extraction succeeded
     */
    public static boolean extractLoveFromExe(Path balatroExe, Path outputLove) {
        LOGGER.info("Extracting LOVE data from {} to {}", balatroExe, outputLove);

        try {
            Files.createDirectories(outputLove.getParent());

            // Balatro.exe is a LOVE game packed as a ZIP archive.
            // The ZIP starts with PK\x03\x04 (0x504B0304).
            // Scan for the magic bytes to handle .exe wrappers with a stub prefix.
            try (RandomAccessFile raf = new RandomAccessFile(balatroExe.toFile(), "r")) {
                long zipOffset = findZipStart(raf);
                if (zipOffset < 0) {
                    LOGGER.error("Could not locate ZIP archive in Balatro.exe");
                    return false;
                }

                raf.seek(zipOffset);

                // Copy the ZIP data directly as .love (it's already a valid ZIP/LOVE archive)
                try (FileOutputStream fos = new FileOutputStream(outputLove.toFile());
                     ZipInputStream zis = new ZipInputStream(new DataInputStream(new RandomAccessStream(raf)))) {

                    // Validate that the ZIP is readable before copying
                    zis.getNextEntry();
                    zis.closeEntry();

                    // Reset to start of ZIP data for raw copy
                    raf.seek(zipOffset);
                    long remaining = raf.length() - zipOffset;
                    byte[] buffer = new byte[65536];
                    long total = 0;
                    while (remaining > 0) {
                        int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read <= 0) break;
                        fos.write(buffer, 0, read);
                        total += read;
                        remaining -= read;
                    }
                    LOGGER.info("Extracted .love ({} bytes) at offset {}", total, zipOffset);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to extract LOVE data from Balatro.exe", e);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error during extraction", e);
            return false;
        }

        return true;
    }

    /**
     * Extracts the ZIP contents into a directory (used for applying web compatibility patches).
     */
    public static boolean extractLoveToDir(Path balatroExe, Path targetDir) {
        LOGGER.info("Extracting LOVE files from {} to {}", balatroExe, targetDir);

        try {
            Files.createDirectories(targetDir);

            try (RandomAccessFile raf = new RandomAccessFile(balatroExe.toFile(), "r");
                 ZipInputStream zis = findAndOpenZip(raf)) {

                if (zis == null) {
                    LOGGER.error("Could not locate ZIP archive in Balatro.exe");
                    return false;
                }

                extractZip(zis, targetDir);
                LOGGER.info("LOVE extraction complete");
                return true;
            } catch (IOException e) {
                LOGGER.error("Failed to extract LOVE data from Balatro.exe", e);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error during extraction", e);
            return false;
        }
    }

    /**
     * Repackages extracted game files into a .love archive.
     * <p>All entries are written STORED (no compression) with explicit CRC,
     * size, and compressed size. STORED output never sets the data-descriptor
     * flag (bit 0x08), which love.js's PhysFS-based ZIP reader cannot parse.
     *
     * @param sourceDir directory containing the .love contents
     * @param loveFile  output .love file path
     * @return true if repacking succeeded
     */
    public static boolean createLoveFile(Path sourceDir, Path loveFile) {
        LOGGER.info("Creating .love file at {}", loveFile);

        try (FileOutputStream fos = new FileOutputStream(loveFile.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                String entryName = sourceDir.relativize(path).toString()
                        .replace(File.separatorChar, '/');
                try {
                    byte[] data = Files.readAllBytes(path);
                    ZipEntry entry = new ZipEntry(entryName);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    entry.setCrc(crc.getValue());
                    entry.setSize(data.length);
                    entry.setCompressedSize(data.length);
                    entry.setMethod(ZipEntry.STORED);
                    zos.putNextEntry(entry);
                    zos.write(data);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            LOGGER.info("Created .love file: {}", loveFile);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to create .love file", e);
            return false;
        }
    }

    private static long findZipStart(RandomAccessFile raf) throws IOException {
        byte[] zipMagic = {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04};
        long fileLength = raf.length();
        if (fileLength < 4) {
            return -1;
        }

        int chunkSize = 65536;
        byte[] buffer = new byte[chunkSize];
        long searchPos = 0;

        while (searchPos < fileLength - 3) {
            int toRead = (int) Math.min(chunkSize, fileLength - searchPos);
            raf.seek(searchPos);
            raf.readFully(buffer, 0, toRead);

            for (int i = 0; i <= toRead - 4; i++) {
                if (buffer[i] == zipMagic[0] && buffer[i + 1] == zipMagic[1] &&
                        buffer[i + 2] == zipMagic[2] && buffer[i + 3] == zipMagic[3]) {
                    return searchPos + i;
                }
            }
            searchPos += toRead - 3;
        }

        return -1;
    }

    private static ZipInputStream findAndOpenZip(RandomAccessFile raf) throws IOException {
        long zipOffset = findZipStart(raf);
        if (zipOffset < 0) {
            return null;
        }
        raf.seek(zipOffset);
        return new ZipInputStream(new DataInputStream(new RandomAccessStream(raf)));
    }

    private static void extractZip(ZipInputStream zis, Path targetDir) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                Files.createDirectories(targetDir.resolve(entry.getName()));
                continue;
            }

            Path filePath = targetDir.resolve(entry.getName()).normalize();
            if (!filePath.startsWith(targetDir)) {
                LOGGER.warn("Skipping unsafe path in archive: {}", entry.getName());
                continue;
            }

            Files.createDirectories(filePath.getParent());
            Files.copy(zis, filePath);
            zis.closeEntry();
        }
    }

    /**
     * Patches a .love archive in-place for love.js compatibility.
     * <p>Applies the following patches (based on W0W53R/web-balatro):
     * <ul>
     *   <li>Injects bit.lua, web_patches.lua, nativefs.lua, lovely.lua into the archive</li>
     *   <li>Patches main.lua: adds require("web_patches") and disables OS check</li>
     *   <li>Patches globals.lua: sets F_SOUND_THREAD = false</li>
     *   <li>Patches resources/shaders/hologram.fs: replaces glow_samples; with 4;</li>
     * </ul>
     * <p>All ZIP entries are written STORED (no compression) with explicit CRC,
     * size, and compressed size. STORED output never sets the data-descriptor
     * flag (bit 0x08), which love.js's PhysFS-based ZIP reader cannot parse.
     *
     * @param loveFile Path to the .love file to patch
     * @return true if patching succeeded
     */
    public static boolean patchLoveArchive(Path loveFile) {
        LOGGER.info("Patching .love file at {}", loveFile);

        File tempFile = null;
        try (ZipFile zipFile = new ZipFile(loveFile.toFile())) {

            tempFile = File.createTempFile("love_patched", ".love", loveFile.getParent().toFile());
            tempFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                byte[] buffer = new byte[65536];

                for (java.util.zip.ZipEntry entry : java.util.Collections.list(zipFile.entries())) {
                    String entryName = entry.getName();
                    byte[] data;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        data = is.readAllBytes();
                    }

                    data = patchEntry(entryName, data);

                    ZipEntry newEntry = new ZipEntry(entryName);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    newEntry.setCrc(crc.getValue());
                    newEntry.setSize(data.length);
                    newEntry.setCompressedSize(data.length);
                    newEntry.setMethod(ZipEntry.STORED);
                    zos.putNextEntry(newEntry);
                    zos.write(data);
                    zos.closeEntry();
                }

                injectPatchFiles(zos);

            }

        } catch (IOException e) {
            LOGGER.error("Failed to patch .love file", e);
            if (tempFile != null) {
                try { tempFile.delete(); } catch (Exception ignored) {}
            }
            return false;
        }

        try {
            Files.deleteIfExists(loveFile);
            Files.move(tempFile.toPath(), loveFile);
            LOGGER.info("Patched .love file successfully");
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to replace patched .love file", e);
            try { if (tempFile != null) tempFile.delete(); } catch (Exception ignored) {}
            return false;
        }
    }

    private static byte[] patchEntry(String entryName, byte[] data) {
        if (entryName.equals("main.lua")) {
            String content = new String(data, StandardCharsets.UTF_8);
            content = "require 'web_patches'\n" + content;
            content = content.replace("if os == 'OS X' or os == 'Windows' then", "if false then");
            content += "\n    G.SOUND_MANAGER = { channel = { push = function() end } }";
            return content.getBytes(StandardCharsets.UTF_8);
        }
        if (entryName.equals("globals.lua")) {
            String content = new String(data, StandardCharsets.UTF_8);
            content = content.replace("F_SOUND_THREAD = true", "F_SOUND_THREAD = false");
            return content.getBytes(StandardCharsets.UTF_8);
        }
        if (entryName.equals("resources/shaders/hologram.fs")) {
            String content = new String(data, StandardCharsets.UTF_8);
            content = content.replace("glow_samples;", "4;");
            return content.getBytes(StandardCharsets.UTF_8);
        }
        return data;
    }

    private static void injectPatchFiles(ZipOutputStream zos) throws IOException {
        String[] patches = {"bit.lua", "web_patches.lua", "nativefs.lua", "lovely.lua"};
        for (String patchName : patches) {
            String resourcePath = "assets/craftlatro/lua/" + patchName;
            try (InputStream is = LoveExtractor.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    LOGGER.warn("Could not find patch resource: {}", resourcePath);
                    continue;
                }
                byte[] data = is.readAllBytes();
                ZipEntry entry = new ZipEntry(patchName);
                CRC32 crc = new CRC32();
                crc.update(data);
                entry.setCrc(crc.getValue());
                entry.setSize(data.length);
                entry.setCompressedSize(data.length);
                entry.setMethod(ZipEntry.STORED);
                zos.putNextEntry(entry);
                zos.write(data);
                zos.closeEntry();
                LOGGER.info("Injected patch file: {} ({} bytes)", patchName, data.length);
            }
        }
    }

    private static class RandomAccessStream extends InputStream {
        private final RandomAccessFile raf;

        RandomAccessStream(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public int read() throws IOException {
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return raf.read(b, off, len);
        }
    }
}
