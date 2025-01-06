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

import static com.android.tools.asdriver.tests.MemoryUsageReportProcessorKt.COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS;
import static com.android.tools.asdriver.tests.MemoryUsageReportProcessorKt.DUMP_HPROF_SNAPSHOT;

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.base.IdeInstallation;
import com.android.tools.asdriver.tests.metric.StudioEvents;
import com.android.tools.asdriver.tests.metric.Telemetry;
import com.android.utils.FileUtils;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.util.system.CpuArch;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class AndroidStudioInstallation extends IdeInstallation<AndroidStudio> {

  // File for storing memory usage statistics. The file is written by calling the `CollectMemoryUsageStatisticsInternalAction` action.
  // After migrating to gRPC API should be removed as a part of b/256132435.
  private final LogFile memoryReportFile;
  private final StudioEvents studioEvents;
  private final Telemetry telemetry;

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

    setConsentGranted(true);
    bundlePlugin(TestUtils.getBinPath("tools/adt/idea/as-driver/asdriver.plugin-studio-sdk.zip"));

    TestLogger.log("AndroidStudioInstallation created with androidStudioFlavor== %s" , androidStudioFlavor);
  }

  public void bundlePlugin(Path pluginZipPath) throws IOException {
    if (!Files.exists(pluginZipPath)) {
      throw new IllegalStateException("Plugin zip file wasn't found. Path: " + pluginZipPath);
    }

    Path pluginsDir = studioDir.resolve("plugins");
    Files.createDirectories(pluginsDir);

    unzip(pluginZipPath, pluginsDir);
  }

  /** Removes the plugin under the provided folder name, under `android-studio/plugins/` */
  public void removePlugin(String folderName) {
    deleteDirectoryRecursively(workDir.resolve("android-studio/plugins/" + folderName));
  }

  /** Deletes the target directory and all of of its items */
  private void deleteDirectoryRecursively(Path directoryPath) {
    try {
      Files.walk(directoryPath)
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
      TestLogger.log("Successfully deleted directory: %s", directoryPath);
    } catch (IOException e) {
      System.err.println("Error deleting directory: " + directoryPath + " - " + e.getMessage());
    }
  }

  @Override
  protected void createVmOptions(StringBuilder vmOptions) throws IOException {
    super.createVmOptions(vmOptions);

    Path threadingCheckerAgentZip = TestUtils.getBinPath("tools/base/threading-agent/threading_agent.jar");
    if (!Files.exists(threadingCheckerAgentZip)) {
      // Threading agent can be built using 'bazel build //tools/base/threading-agent:threading_agent'
      throw new IllegalStateException("Threading checker agent not found at " + threadingCheckerAgentZip);
    }

    vmOptions.append(String.format("-javaagent:%s%n", threadingCheckerAgentZip));
    // Need to disable android first run checks, or we get stuck in a modal dialog complaining about lack of web access.
    vmOptions.append(String.format("-Dgradle.ide.save.log.to.file=true%n"));
    // Prevent our crash metrics from going to the production URL
    vmOptions.append(String.format("-Duse.staging.crash.url=true%n"));
    // Enabling this flag is required for connecting all the Java Instrumentation agents needed for memory statistics.
    vmOptions.append(String.format("-Dstudio.run.under.integration.test=true%n"));
    vmOptions.append(String.format("-Djdk.attach.allowAttachSelf=true%n"));
    // Always collect histograms
    vmOptions.append(String.format("-D%s=true%n", COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS));
    if (Boolean.getBoolean(DUMP_HPROF_SNAPSHOT)) {
      vmOptions.append(String.format("-D%s=true%n", DUMP_HPROF_SNAPSHOT));
    }

    // Specify the folder where completion variants for all the calls of the %doComplete performanceTesting command will be stored, same
    // file is used by the %assertCompletionCommand command to access the output of the last completion event.
    vmOptions.append(
      String.format("-Dcompletion.command.report.dir=%s%n", Files.createTempDirectory(TestUtils.getTestOutputDir(), "completion_report")));
    // Specify the file where the results of the findUsages action triggered by the %findUsages command will be stored. The same file is
    // used by the %assertFindUsagesEntryCommand to access the result of the last findUsages event.
    vmOptions.append(
      String.format("-Dfind.usages.command.found.usages.list.file=%s%n", TestUtils.getTestOutputDir().resolve("find.usages.list.txt")));
  }

  public void enableBleak() throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(vmOptionsPath.toFile(), true))) {
      try {
        Path jvmtiAgent = TestUtils.resolveWorkspacePath(
          "tools/adt/idea/bleak/src/com/android/tools/idea/bleak/agents/libjnibleakhelper.so").toRealPath();
        writer.append(String.format("-agentpath:%s%n", jvmtiAgent));
        writer.append(String.format("-Denable.bleak=true%n"));
        writer.append(String.format("-Dbleak.jvmti.enabled=true%n"));
      }
      catch (IOException ignored) {
        throw new IllegalStateException("BLeak JVMTI agent not found");
      }
    }
  }

  /**
   * Emits the agent's logs to the console. When running a test locally, this can be helpful for
   * viewing the logs without having to suspend any processes; without this, you would have to
   * manually locate the randomly created temporary directories holding the logs.
   */
  public void debugEmitLogs() {
    try {
      stdout.printContents();
      stderr.printContents();
      ideaLog.printContents();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Writes a file to disk that will make the platform think that the user has already interacted
   * with the "Send usage statistics to Google" dialog. This process is faster than automating the
   * consent dialog since the IDE checks for consent at start-up anyway.
   *
   * The internals of this function must match {@link com.intellij.ide.gdpr.ConfirmedConsent}.
   * @param granted Whether the user grants consent.
   * @throws IOException If there's a problem writing the file.
   * @see com.intellij.ide.gdpr.ConfirmedConsent ConfirmedConsent
   */
  public void setConsentGranted(boolean granted) throws IOException {
    String STATISTICS_OPTION_ID = "rsch.send.usage.stat";
    String EAP_FEEDBACK_OPTION_ID = "eap";

    // Only the major version matters for ConsentOptions#needsReconfirm.
    String version = "1.0";
    String isAccepted = granted ? "1" : "0";
    long time = Instant.now().getEpochSecond() * 1000;
    String consentFormatString = "%s:%s:%s:%d";
    String nonEapString = String.format(consentFormatString, STATISTICS_OPTION_ID, version, isAccepted, time);
    String eapString = String.format(consentFormatString, EAP_FEEDBACK_OPTION_ID, version, isAccepted, time);
    String combinedString = String.format("%s;%s", nonEapString, eapString);

    Path consentOptions = workDir.resolve("data/Google/consentOptions/accepted");
    if (SystemInfo.isMac) {
      consentOptions = fileSystem.getHome().resolve("Library/Application Support/Google/consentOptions/accepted");
    } else if (SystemInfo.isWindows) {
      // Since we're running from outside of Android Studio, getCommonDataPath() will have a vendor
      // name of "null" instead of "Google", so we work around that here.
      consentOptions = PathManager.getCommonDataPath().getParent().resolve("Google/consentOptions/accepted");
    }
    Files.createDirectories(consentOptions.getParent());
    Files.writeString(consentOptions, combinedString);
  }


  /**
   * Changes the settings not to show balloon notifications.
   */
  public void disableBalloonNotifications() throws IOException {
    Path filetypePaths = configDir.resolve("options/notifications.xml");
    Files.createDirectories(filetypePaths.getParent());
    String filetypeContents =
      """
      <application>
        <component name="NotificationConfiguration" showBalloons="false"/>
      </application>""";
    Files.writeString(filetypePaths, filetypeContents, StandardCharsets.UTF_8);
  }

  /**
   * Accept the legal notice about showing decompiler .class files in editor.
   */
  public void acceptLegalDecompilerNotice() throws IOException {
    Path filetypePaths = configDir.resolve("options/other.xml");
    Files.createDirectories(filetypePaths.getParent());
    String filetypeContents =
      """
        <application>
          <component name="PropertyService"><![CDATA[{
          "keyToString": {
            "decompiler.legal.notice.accepted": "true"
          }
          }]]></component>
        </application>""";
    Files.writeString(filetypePaths, filetypeContents, StandardCharsets.UTF_8);
  }

  /**
   * Sets the SDK for the entire IDE rather than for a single project. Projects will then inherit
   * this value.
   */
  public void setGlobalSdk(AndroidSdk sdk) throws IOException {
    Path filetypePaths = configDir.resolve("options/other.xml");

    if (filetypePaths.toFile().exists()) {
      throw new IllegalStateException(
        String.format("%s already exists, which means this method should be changed to merge with it rather than overwriting it.",
                      filetypePaths));
    }

    Files.createDirectories(filetypePaths.getParent());

    // Make sure backslashes don't show up in the path on Windows since the blob below must be valid JSON
    String sdkPath = sdk.getSourceDir().toString().replaceAll("\\\\", "/");
    String filetypeContents = String.format(
      "<application>%n" +
      "  <component name=\"PropertyService\"><![CDATA[{%n" +
      "  \"keyToString\": {%n" +
      "    \"android.sdk.path\": \"%s\"%n" +
      "  }%n" +
      "}]]></component>%n" +
      "</application>", sdkPath);
    Files.writeString(filetypePaths, filetypeContents, StandardCharsets.UTF_8);
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

  /**
   * Creating {@code androidStudioFirstRun.xml} is what lets us bypass the Welcome Wizard.
   * @throws IOException If the file can't be created.
   */
  public void createFirstRunXml() throws IOException {
    Path dest = configDir.resolve("options/androidStudioFirstRun.xml");
    TestLogger.log("Creating - %s", dest);

    Files.createDirectories(dest.getParent());
    String firstRunContents =
      "<application>\n" +
      "  <component name=\"AndroidFirstRunPersistentData\">\n" +
      "    <version>1</version>\n" +
      "  </component>\n" +
      "</application>\n";
    Files.writeString(dest, firstRunContents, StandardCharsets.UTF_8);
  }

  public void setNewUi() throws IOException {
    Path dest = configDir.resolve(EarlyAccessRegistryManager.fileName);
    TestLogger.log("Creating - %s", dest);
    Files.createDirectories(dest.getParent());
    String contents =
      "ide.experimental.ui\n" +
      "true\n" +
      "ide.experimental.ui.inter.font\n" +
      "false\n" +
      "idea.plugins.compatible.build";
    Files.writeString(dest, contents, StandardCharsets.UTF_8);
  }

  public void createGeneralPropertiesXml() throws IOException {
    Path dest = configDir.resolve("options").resolve("ide.general.xml");
    TestLogger.log("Creating - %s", dest);
    Files.createDirectories(dest.getParent());
    String registryChanges = "";
    if (SystemInfo.isWindows) {
      // When run in a Windows Docker container, we hit this issue:
      // https://youtrack.jetbrains.com/issue/IDEA-270104. The resulting error doesn't seem to
      // crash Android Studio, but the stack traces take up more than 100 lines in the log, so we
      // work around the issue by disabling jump lists on Windows.
      registryChanges =
        "  <component name=\"Registry\">\n" +
        "    <entry key=\"windows.jumplist\" value=\"false\" />\n" +
        "  </component>\n";
    }

    String generalPropertyContents =
      "<application>\n" +
      "  <component name=\"GeneralSettings\">\n" +
      "    <option name=\"confirmExit\" value=\"false\" />\n" +
      "    <option name=\"processCloseConfirmation\" value=\"TERMINATE\" />\n" +
      "  </component>\n" +
      registryChanges +
      "</application>";
    Files.writeString(dest, generalPropertyContents, StandardCharsets.UTF_8);
  }

  public Path getConfigDir() {
    return configDir;
  }

  public Telemetry getTelemetry() {
    return telemetry;
  }

  public StudioEvents getStudioEvents() {
    return studioEvents;
  }

  public Path getAndroidStudioProjectsDir() {
    return fileSystem.getHome().resolve("AndroidStudioProjects");
  }

  public LogFile getMemoryReportFile() {
    return memoryReportFile;
  }

  /**
   * Run from an APK project, which is different from an {@link AndroidProject} in that it doesn't
   * have Gradle files or a {@code local.properties} file.
   *
   * Running from the "File" → "Profile or Debug APK" flow would also work, but that requires more
   * automation to set up.
   */
  public AndroidStudio runFromExistingProject(Display display, HashMap<String, String> env, String path) throws IOException, InterruptedException {
    Path project = TestUtils.resolveWorkspacePath(path);
    Path targetProject = Files.createTempDirectory(fileSystem.getRoot(), "project");
    FileUtils.copyDirectory(project.toFile(), targetProject.toFile());

    trustPath(targetProject);
    return run(display, env, new String[]{ targetProject.toString() });
  }

  public void forceSafeMode() {
    this.forceSafeMode = true;
  }

  public void trustPath(Path path) throws IOException {
    Path trustedPaths = configDir.resolve("options").resolve("trusted-paths.xml");
    Files.createDirectories(trustedPaths.getParent());
    Files.writeString(trustedPaths, "<application>\n" +
                                    "  <component name=\"Trusted.Paths\">\n" +
                                    "    <option name=\"TRUSTED_PROJECT_PATHS\">\n" +
                                    "      <map>\n" +
                                    "        <entry key=\"" + path + "\" value=\"true\" />\n" +
                                    "      </map>\n" +
                                    "    </option>\n" +
                                    "  </component>\n" +
                                    "  <component name=\"Trusted.Paths.Settings\">\n" +
                                    "    <option name=\"TRUSTED_PATHS\">\n" +
                                    "      <list>\n" +
                                    "        <option value=\"" + path.getParent() + "\" />\n" +
                                    "      </list>\n" +
                                    "    </option>\n" +
                                    "  </component>\n" +
                                    "</application>");
  }

  public void verify() throws IOException {
    checkLogsForThreadingViolations();
  }

  private void checkLogsForThreadingViolations() throws IOException {
    boolean hasThreadingViolations =
      ideaLog.hasMatchingLine(".*Threading violation.+(@UiThread|@WorkerThread).*");
    if (hasThreadingViolations) {
      throw new RuntimeException("One or more methods called on a wrong thread. " +
                                 "See go/android-studio-threading-checks for more info.");
    }
  }

  public AndroidStudio run(Display display, Map<String, String> env, AndroidProject project, Path sdkDir) throws IOException, InterruptedException {
    Path projectPath = project.install(fileSystem.getRoot());
    project.setSdkDir(sdkDir);
    // Mark that project as trusted
    trustPath(projectPath);
    return run(display, env, new String[]{ projectPath.toString() });
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
      studioExecutable = "android-studio/bin/studio.sh";
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
  protected AndroidStudio createAndAttach() throws IOException, InterruptedException {
    AndroidStudio studio = attach();
    studio.startCapturingScreenshotsOnWindows();
    return studio;
  }

  @Override
  protected String vmOptionEnvName() {
    return "STUDIO_VM_OPTIONS";
  }

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

  /**
   * Checks to see if Android Studio failed to start because the JDWP address was already in use.
   * This method will throw an exception in the test process rather than the developer having to
   * check Android Studio's stderr itself. I.e. this method is purely for developer convenience
   * when testing locally.
   */
  static private void checkForJdwpError(AndroidStudioInstallation installation) {
    try {
      List<String> stderrContents = Files.readAllLines(installation.getStderr().getPath());
      boolean hasJdwpError = stderrContents.stream().anyMatch((line) -> line.contains("JDWP exit error AGENT_ERROR_TRANSPORT_INIT"));
      boolean isAddressInUse = stderrContents.stream().anyMatch((line) -> line.contains("Address already in use"));
      if (hasJdwpError && isAddressInUse) {
        throw new IllegalStateException("The JDWP address is already in use. You can fix this either by removing your " +
                                        "AS_TEST_DEBUG env var or by terminating the existing Android Studio process.");
      }
    }
    catch (IOException e) {
      // We tried our best. :(
    }
  }

  static private int waitForDriverPid(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver started on pid: (\\d+).*", null, true, 120, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  static private int waitForDriverServer(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver server listening at: (\\d+).*", null, true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
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
}
