/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import com.android.SdkConstants;
import com.android.testutils.TestUtils;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public class Emulator implements AutoCloseable {
  private final TestFileSystem fileSystem;
  private final AndroidSdk sdk;
  private final LogFile logFile;
  private final LogFile logCat;
  private final String portString;
  private final Process process;
  private final String name;

  public static void createEmulator(TestFileSystem fileSystem, String name, Path systemImage) throws IOException {
    Path avdHome = getAvdHome(fileSystem);
    Files.createDirectories(avdHome);

    Path sourceProperties = systemImage.resolve("source.properties");
    Matcher api = getString(sourceProperties, "AndroidVersion.ApiLevel=(.*)");
    Matcher abi = getString(sourceProperties, "SystemImage.Abi=(.*)");

    Path emuIni = avdHome.resolve(name + ".ini");
    Files.createFile(emuIni);
    try (FileWriter writer = new FileWriter(emuIni.toFile())) {
      writer.write(String.format("avd.ini.encoding=UTF-8%n"));
      writer.write(String.format("path=%s/%s.avd%n", avdHome, name));
      writer.write(String.format("path.rel=avd/%s.avd%n", name));
      writer.write(String.format("target=android-%s%n", api.group(1)));
    }

    Path configIni = avdHome.resolve(name + ".avd").resolve("config.ini");
    Files.createDirectories(configIni.getParent());
    try (FileWriter writer = new FileWriter(configIni.toFile())) {
      writer.write(String.format("PlayStore.enabled=false%n"));
      writer.write(String.format("abi.type=%s%n", abi.group(1)));
      writer.write(String.format("avd.ini.encoding=UTF-8%n"));
      writer.write(String.format("hw.accelerometer=yes%n"));
      writer.write(String.format("hw.audioInput=yes%n"));
      writer.write(String.format("hw.battery=yes%n"));
      writer.write(String.format("hw.cpu.arch=%s%n", abi.group(1)));
      writer.write(String.format("hw.dPad=no%n"));
      writer.write(String.format("hw.device.hash2=MD5:524882cfa9f421413193056700a29392%n"));
      writer.write(String.format("hw.device.manufacturer=Google%n"));
      writer.write(String.format("hw.device.name=pixel%n"));
      writer.write(String.format("hw.gps=yes%n"));
      writer.write(String.format("hw.lcd.density=480%n"));
      writer.write(String.format("hw.lcd.height=1920%n"));
      writer.write(String.format("hw.lcd.width=1080%n"));
      writer.write(String.format("hw.mainKeys=no%n"));
      writer.write(String.format("hw.sdCard=yes%n"));
      writer.write(String.format("hw.sensors.orientation=yes%n"));
      writer.write(String.format("hw.sensors.proximity=yes%n"));
      writer.write(String.format("hw.trackBall=no%n"));
      writer.write(String.format("image.sysdir.1=%s%n", systemImage));
    }
  }

  public static Emulator start(TestFileSystem fileSystem,
                               AndroidSdk sdk,
                               Display display,
                               String name,
                               int grpcPort,
                               List<String> extraEmulatorFlags) throws IOException, InterruptedException {
    Path logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "emulator_logs");

    LogFile logCat = new LogFile(logsDir.resolve(name + "_logcat.txt"));

    List<String> procArgs = new ArrayList<>(Arrays.asList(
      sdk.getSourceDir().resolve(SdkConstants.FD_EMULATOR).resolve("emulator").toString(),
      "@" + name,
      // This port value needs to be unique for each emulator
      "-grpc", Integer.toString(grpcPort),
      "-no-snapshot",
      // Turn off the modem simulator to avoid b/258836512
      // Turn off Vulkan since API 30 has a broken implementation (and we don't need it anyway) b/274524732
      "-feature",
      "-ModemSimulator,-Vulkan",
      "-delay-adb",
      "-no-boot-anim",
      "-verbose",
      "-logcat",
      "*:V",
      "-logcat-output",
      logCat.getPath().toFile().getAbsolutePath()
    ));

    procArgs.addAll(extraEmulatorFlags);

    ProcessBuilder pb = new ProcessBuilder(procArgs.toArray(new String[0]));
    pb.environment().put("HOME", fileSystem.getHome().toString());
    pb.environment().put("ANDROID_EMULATOR_HOME", fileSystem.getAndroidHome().toString());
    pb.environment().put("ANDROID_AVD_HOME", getAvdHome(fileSystem).toString());
    pb.environment().put("ANDROID_SDK_ROOT", sdk.getSourceDir().toString());
    pb.environment().put("ANDROID_PREFS_ROOT", fileSystem.getHome().toString());
    if (display.getDisplay() != null) {
      pb.environment().put("DISPLAY", display.getDisplay());
    }
    // On older emulators in a remote desktop session, the hardware acceleration won't start properly without this env var.
    pb.environment().put("CHROME_REMOTE_DESKTOP_SESSION", "1");

    LogFile logFile = new LogFile(logsDir.resolve(name + "_stdout.txt"));
    pb.redirectOutput(logFile.getPath().toFile());
    pb.redirectError(Files.createFile(logsDir.resolve(name + "_stderr.txt")).toFile());
    Process process = pb.start();

    // There's no easy/reliable way to determine whether an emulator even CAN start on this
    // machine, so we check for the process crashing, that way we can report why the test will
    // fail and potentially cut down on confusion while investigating.
    new Thread(() -> {
      try {
        Thread.sleep(10000);
      }
      catch (InterruptedException e) {
        // ignore
      }
      if (!process.isAlive()) {
        int exitCode = process.exitValue();
        if (exitCode != 0) {
          System.err.printf("Emulator process (PID=%d) exited unexpectedly with code==%d. If you are running on a VM, it's possible that " +
                            "nested virtualization is not supported. To test this, you can try starting the emulator manually. Most " +
                            "likely though, if you're seeing this message, it means that the emulator won't work on your machine.%n",
                            process.pid(), exitCode);
        }
      }
    }).start();

    String portString =
      logFile.waitForMatchingLine(".*control console listening on port (\\d+), ADB on port \\d+", 2, TimeUnit.MINUTES).group(1);

    return new Emulator(fileSystem, sdk, logFile, logCat, portString, process, name);
  }

  private Emulator(TestFileSystem fileSystem, AndroidSdk sdk, LogFile logFile, LogFile logCat, String portString, Process process, String name) {
    this.fileSystem = fileSystem;
    this.sdk = sdk;
    this.logFile = logFile;
    this.logCat = logCat;
    this.portString = portString;
    this.process = process;
    this.name = name;
  }

  public void waitForBoot() throws IOException, InterruptedException {
    if (process == null) {
      throw new IllegalStateException("Emulator not running yet.");
    }
    logFile.waitForMatchingLine(".*Boot completed.*", 12, TimeUnit.MINUTES);
  }

  public Path getHome() {
    return fileSystem.getHome();
  }

  public AndroidSdk getSdk() {
    return sdk;
  }

  public LogFile getLogCat() {
    return logCat;
  }

  public String getPortString() {
    return portString;
  }

  public String getName() {
    return name;
  }

  public String getSerialNumber() {
    // In accordance with https://cs.android.com/android/platform/superproject/+/master:packages/modules/adb/SERVICES.TXT
    return "emulator-" + portString;
  }

  @Override
  public void close() throws InterruptedException {
    if (process != null) {
      process.destroy();
      process.waitFor();
    }
  }

  private static Path getAvdHome(TestFileSystem fileSystem) {
    return fileSystem.getAndroidHome().resolve("avd");
  }

  private static Matcher getString(Path file, String regex) throws IOException {
    String fileString = Files.readString(file);
    Matcher matcher = Pattern.compile(regex).matcher(fileString);
    if (!matcher.find()) {
      throw new IllegalStateException(String.format("Regex '%s' not found in %s", regex, file));
    }
    return matcher;
  }

  /** A particular supported {@link Emulator} image to use. */
  public enum SystemImage {
    // Google API, userdebug builds
    API_30("system_image_android-30_default_x86_64"),
    API_31("system_image_android-31_default_x86_64"),
    API_33("system_image_android-33_default_x86_64"),
    API_35("system_image_android-35_default_x86_64"),
    // Android Automated Test Device system image
    API_33_ATD("system_image_android-33_aosp_atd_x86_64"),
    // Google Play builds
    API_33_PlayStore("system_image_android-33PlayStore_default_x86_64");
    /** Path to the image for this emulator {@link SystemImage}. */
    @NotNull
    public final String path;

    private SystemImage(@NotNull String path) {
      this.path = path;
    }
  }
}
