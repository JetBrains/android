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

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.base.IdeInstallation;
import com.android.tools.asdriver.tests.metric.IndexingMetrics;
import com.android.tools.asdriver.tests.metric.StudioEvents;
import com.android.tools.asdriver.tests.metric.Telemetry;
import com.android.tools.testlib.LogFile;
import com.android.tools.testlib.TestFileSystem;
import com.android.tools.testlib.TestLogger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AndroidStudioInstallation extends IdeInstallation<AndroidStudio> {

  // File for storing memory usage statistics. The file is written by calling the `CollectMemoryUsageStatisticsInternalAction` action.
  // After migrating to gRPC API should be removed as a part of b/256132435.
  private final LogFile memoryReportFile;
  private final StudioEvents studioEvents;
  private final Telemetry telemetry;
  private final IndexingMetrics indexingMetrics;

  private boolean forceSafeMode = false;

  public static AndroidStudioInstallation fromZip(TestFileSystem testFileSystem) throws IOException {
    Options options = new Options(testFileSystem);
    return fromZip(options);
  }

  public static AndroidStudioInstallation fromZip(TestFileSystem testFileSystem, AndroidStudioFlavor androidStudioFlavor)
    throws IOException {
    Options options = new Options(testFileSystem, androidStudioFlavor);
    return fromZip(options);
  }

  public static AndroidStudioInstallation fromZip(Options options) throws IOException {
    Path workDir = Files.createTempDirectory(options.testFileSystem.getRoot(), "android-studio");
    TestLogger.log("workDir: %s", workDir);
    String platform = "linux";
    if (SystemInfo.isMac) {
      if (SystemInfo.OS_ARCH.equals("aarch64")) {
        platform = "mac_arm";
      } else {
        platform = "mac";
      }
    } else if (SystemInfo.isWindows) {
      platform = "win";
    }

    String zipPath;
    switch (options.androidStudioFlavor) {
      case FOR_EXTERNAL_USERS:
        zipPath = String.format("tools/adt/idea/studio/android-studio.%s.zip", platform);
        break;
      case ASWB:
        zipPath = String.format("tools/vendor/google/aswb/aswb.%s.zip", platform);
        break;
      case ASFP:
        zipPath = String.format("tools/vendor/google/asfp/studio/asfp.%s.zip", platform);
        break;
      default:
        throw new IllegalArgumentException("A valid AndroidStudioFlavor must be passed in. Got: " + options.androidStudioFlavor);
    }
    Path studioZip = TestUtils.getBinPath(zipPath);
    unzip(studioZip, workDir);

    String studioDir = getStudioDirectory(workDir);
    return new AndroidStudioInstallation(options.testFileSystem, workDir, workDir.resolve(studioDir), options.androidStudioFlavor, options.disableFirstRun);
  }

  static public AndroidStudioInstallation fromDir(TestFileSystem testFileSystem) throws IOException {
    Options options = new Options(testFileSystem, AndroidStudioFlavor.FOR_EXTERNAL_USERS);
    return fromDir(options);
  }

  public static AndroidStudioInstallation fromDir(TestFileSystem testFileSystem, AndroidStudioFlavor androidStudioFlavor)
    throws IOException {
    Options options = new Options(testFileSystem, androidStudioFlavor);
    return fromDir(options);
  }

  static public AndroidStudioInstallation fromDir(Options options) throws IOException {
    String androidStudioDirectory;
    switch (options.androidStudioFlavor) {
      case FOR_EXTERNAL_USERS:
        androidStudioDirectory = "tools/adt/idea/studio/android-studio";
        break;
      case ASWB:
        androidStudioDirectory = "tools/vendor/google/aswb/aswb";
        break;
      case ASFP:
        androidStudioDirectory = "tools/vendor/google/asfp/studio/asfp";
        break;
      default:
        throw new IllegalArgumentException("A valid AndroidStudioFlavor must be passed in. Got: " + options.androidStudioFlavor);
    }

    Path workDir = TestUtils.getBinPath(androidStudioDirectory);
    Path studioDir = workDir.resolve(getStudioDirectory(workDir));
    TestLogger.log("studioDir: %s", studioDir);
    return new AndroidStudioInstallation(options.testFileSystem, workDir, studioDir, options.androidStudioFlavor, options.disableFirstRun);
  }

  static public AndroidStudioInstallation fromDir(TestFileSystem testFileSystem, Path studioDir) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "android-studio");
    return new AndroidStudioInstallation(testFileSystem, workDir, studioDir, AndroidStudioFlavor.UNKNOWN, true);
  }

  private AndroidStudioInstallation(TestFileSystem testFileSystem,
                                    Path workDir,
                                    Path studioDir,
                                    AndroidStudioFlavor androidStudioFlavor,
                                    Boolean disableFirstRun) throws IOException {
    super("studio-sdk", testFileSystem, workDir, studioDir);

    if (disableFirstRun) {
      this.addVmOption("-Ddisable.android.first.run=true");
    }

    Path studioEventsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "studio_events");
    studioEvents = new StudioEvents(studioEventsDir);
    this.addVmOption(String.format("-Dstudio.event.dump.dir=%s%n", studioEventsDir));

    memoryReportFile = new LogFile(logsDir.resolve("memory_usage_report.log"));
    Files.createFile(memoryReportFile.getPath());

    Path telemetryJsonFile = logsDir.resolve("opentelemetry.json");
    telemetry = new Telemetry(telemetryJsonFile);
    this.addVmOption(String.format("-Didea.diagnostic.opentelemetry.file=%s%n", telemetryJsonFile));

    indexingMetrics = new IndexingMetrics(logsDir.resolve("indexing-diagnostic"));

    setConsentGranted(true);
    bundlePlugin(TestUtils.getBinPath("tools/adt/idea/as-driver/asdriver.plugin-studio-sdk.zip"));

    TestLogger.log("AndroidStudioInstallation created with androidStudioFlavor== %s" , androidStudioFlavor);
  }

  /**
   * Modifies the build number within an Android Studio installation so that it will think it's a
   * different build.
   * @throws IOException
   */
  public void setBuildNumber(String buildNumber) throws IOException {
    Path resourcesJar = studioDir.resolve("lib/resources.jar");
    Path tempDir = Files.createTempDirectory("modify_resources_jar");
    Path unzippedDir = tempDir.resolve("unzipped");
    Files.createDirectories(unzippedDir);
    InstallerUtil.unzip(resourcesJar, unzippedDir, Files.size(resourcesJar), new FakeProgressIndicator());

    Path appInfoXml = unzippedDir.resolve("idea/AndroidStudioApplicationInfo.xml");
    Charset charset = StandardCharsets.UTF_8;
    String xmlContent = Files.readString(appInfoXml, charset);

    // This is more brittle than using XPath, but it's sufficient for now.
    xmlContent = xmlContent.replaceAll("(.*<build number=\")(.*?)(\".*)", String.format("$1%s$3", buildNumber));
    xmlContent = xmlContent.replaceAll("(.*<version major=\".*)", "<version major=\"2022\" minor=\"1\" micro=\"2\" patch=\"3\" full=\"Electric Eel | {0}.{1}.{2} Stable 10\" eap=\"false\"/>");
    Files.write(appInfoXml, xmlContent.getBytes(charset));
    Path newJarPath = tempDir.resolve("resources.jar");
    TestUtils.zipDirectory(unzippedDir, newJarPath);

    Files.delete(resourcesJar);
    Files.copy(newJarPath, resourcesJar);

    Files.write(studioDir.resolve("build.txt"), buildNumber.getBytes(charset));
  }

  public Telemetry getTelemetry() {
    return telemetry;
  }

  public StudioEvents getStudioEvents() {
    return studioEvents;
  }

  public IndexingMetrics getIndexingMetrics() {
    return indexingMetrics;
  }

  public Path getAndroidStudioProjectsDir() {
    return fileSystem.getHome().resolve("AndroidStudioProjects");
  }

  public LogFile getMemoryReportFile() {
    return memoryReportFile;
  }

  public void forceSafeMode() {
    this.forceSafeMode = true;
  }

  @Override
  protected String getExecutable() {
    String studioExecutable;

    if (forceSafeMode) {
      studioExecutable = "android-studio/bin/studio_safe.sh";
      if (SystemInfo.isMac) {
        boolean isPreview = isMacPreview(workDir);
        studioExecutable = isPreview ? "Android Studio Preview.app/Contents/bin/studio_safe.sh" : "Android Studio.app/Contents/bin/studio_safe.sh";
      } else if (SystemInfo.isWindows) {
        studioExecutable = "android-studio/bin/studio_safe.bat";
      }
    } else {
      studioExecutable = "android-studio/bin/studio";
      if (SystemInfo.isMac) {
        boolean isPreview = isMacPreview(workDir);
        studioExecutable = isPreview ? "Android Studio Preview.app/Contents/MacOS/studio" : "Android Studio.app/Contents/MacOS/studio";
      } else if (SystemInfo.isWindows) {
        studioExecutable = String.format("android-studio/bin/studio%s.exe", CpuArch.isIntel32() ? "" : "64");
      }
    }

    return workDir.resolve(studioExecutable).toString();
  }

  @Override
  protected String vmOptionEnvName() {
    return "STUDIO_VM_OPTIONS";
  }

  @Override
  public AndroidStudio attach() throws IOException, InterruptedException {
    int pid;
    try {
      pid = waitForDriverPid(getIdeaLog());
    } catch (InterruptedException e) {
      checkForJdwpError(this);
      throw e;
    }

    ProcessHandle process = ProcessHandle.of(pid).get();
    int port = waitForDriverServer(getIdeaLog());
    return new AndroidStudio(this, process, port);
  }

  public enum AndroidStudioFlavor {
    // This is the most common version of Android Studio and is what can be found on
    // https://developer.android.com/studio.
    FOR_EXTERNAL_USERS,

    // Android Studio with Blaze.
    ASWB,

    // Android Studio for Platform.
    ASFP,

    // This indicates that some operation will need to be performed to determine which flavor is
    // being used.
    UNKNOWN,
  }

  public static class Options {
    public TestFileSystem testFileSystem;
    AndroidStudioFlavor androidStudioFlavor = AndroidStudioFlavor.FOR_EXTERNAL_USERS;
    boolean disableFirstRun = true;

    public Options(TestFileSystem system) {
      testFileSystem = system;
    }

    public Options(TestFileSystem system, AndroidStudioFlavor flavor) {
      testFileSystem = system;
      androidStudioFlavor = flavor;
    }
  }

  private static String getStudioDirectory(Path workDir) {
    if (SystemInfo.isMac) {
      if (isMacPreview(workDir)) {
        return "Android Studio Preview.app/Contents";
      } else {
        return "Android Studio.app/Contents";
      }
    }
    return "android-studio";
  }

  private static boolean isMacPreview(Path workDir) {
    return (SystemInfo.isMac && Files.exists(workDir.resolve("Android Studio Preview.app")));
  }

  @Override
  public void close() throws Exception {}
}