/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea;

import com.android.tools.testlib.Adb;
import com.android.tools.testlib.AndroidSdk;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.Emulator;
import com.android.tools.testlib.TestFileSystem;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility that boots an emulator from scratch and saves its finalized state as a zipped AVD snapshot.
 * Integration tests can load this snapshot to bypass the lengthy Emulator boot process,
 * reducing test setup time significantly.
 *
 * <p><b>Inputs:</b>
 * <ul>
 *   <li>The first command-line argument must be the path where the output zip will be saved.</li>
 *   <li>System properties (typically provided by Bazel):
 *     <ul>
 *       <li><code>emulator.test.sdk.path</code>: Path to the prebuilt Android SDK.</li>
 *       <li><code>emulator.test.system.image.files</code>: Paths to system image files, used to locate <code>source.properties</code>.</li>
 *       <li><code>emulator.test.emulator.path</code>: Path to the emulator executable.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Output:</b>
 * A zip file containing the <code>.avd</code> directory and <code>.ini</code> file.
 *
 * <p><b>Assumptions & Implementation Details:</b>
 * <ul>
 *   <li>Requires a functional emulator environment with hardware acceleration (e.g., KVM on Linux).</li>
 *   <li>To ensure a clean snapshot, the emulator is started with <code>-no-snapshot-load</code> (saveSnapshot=true),
 *       forcing a cold boot.</li>
 *   <li>The utility waits for the "Boot completed" signal in logcat. Once detected, the emulator
 *       is shut down normally. This triggers the emulator to save its state to the 'default_boot'
 *       snapshot before zipping.</li>
 * </ul>
 */
public class EmulatorQuickbootSnapshotGenerator {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Output zip file path must be provided as the first argument.");
        }
        Path outputZip = Paths.get(args[0]);
        Path tempDir = Files.createTempDirectory("emulator_quickboot_gen");
        generateSnapshot(tempDir, outputZip);
    }

    public static void generateSnapshot(Path rootDir, Path outputZip) throws Exception {
        String sdkPath = getPropertyOrThrow("emulator.test.sdk.path");
        AndroidSdk sdk = new AndroidSdk(Paths.get(sdkPath));

        String allFiles = getPropertyOrThrow("emulator.test.system.image.files");
        Path systemImageDir = getPackageDirectory(allFiles.split(" "));

        boolean isEmuNext =
                Optional.ofNullable(System.getProperty("emulator.test.emulator.is-emu-next"))
                        .map(s -> s.equals("1"))
                        .orElse(false);

        String emuBin = getPropertyOrThrow("emulator.test.emulator.path");

        TestFileSystem fileSystem = new TestFileSystem(rootDir);
        String avdName = "emu";

        Emulator.createEmulator(fileSystem, avdName, systemImageDir);

        try (Display display = Display.createDefault();
             Adb adb = Adb.start(sdk, fileSystem);
             Emulator emulator =
                     Emulator.start(
                             fileSystem,
                             Paths.get(emuBin),
                             isEmuNext,
                             sdk.getSourceDir(),
                             display,
                             avdName,
                             8554,
                             new ArrayList<>(),
                             false,
                             true)) {
            emulator.waitForBoot();
            adb.waitForDevice(emulator);
        }

        Path androidHome = fileSystem.getAndroidHome();
        Path avdHome = androidHome.resolve("avd");
        zip(avdHome, outputZip, avdName + ".avd", avdName + ".ini");
        System.out.println("Quickboot snapshot successfully saved to: " + outputZip.toAbsolutePath());
    }

    public static void zip(Path sourceDir, Path zipFile, String... entries) throws IOException {
        Files.createDirectories(zipFile.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            for (String entryName : entries) {
                Path entryPath = sourceDir.resolve(entryName);
                if (Files.exists(entryPath)) {
                    if (Files.isDirectory(entryPath)) {
                        zipDirectory(entryPath, entryName, zos);
                    } else {
                        zipFile(entryPath, entryName, zos);
                    }
                }
            }
        }
    }

    private static void zipDirectory(Path folder, String parentFolder, ZipOutputStream zos)
            throws IOException {
        Files.walk(folder)
                .forEach(
                        path -> {
                            try {
                                if (!Files.isDirectory(path)) {
                                    String name =
                                            parentFolder + "/" + folder.relativize(path).toString();
                                    zipFile(path, name, zos);
                                }
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    private static void zipFile(Path file, String name, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    public static Path getPackageDirectory(String[] files) {
        for (String file : files) {
            file = file.replace("\"", "");
            Path path = Paths.get(file);
            if (!Files.exists(path) && file.startsWith("../")) {
                Path altPath = Paths.get("external").resolve(file.substring(3));
                if (Files.exists(altPath)) {
                    path = altPath;
                }
            }
            Path name = path.getFileName();
            if (name.toString().equals("source.properties")) {
                return path.toAbsolutePath().normalize().getParent();
            }
        }
        throw new IllegalStateException("source.properties not found");
    }

    public static String getPropertyOrThrow(String property) {
        String value = System.getProperty(property);
        if (value == null) {
            throw new IllegalStateException("Property " + property + " must be set.");
        }
        return value;
    }
}
