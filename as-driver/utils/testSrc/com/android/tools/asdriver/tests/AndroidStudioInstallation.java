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
import com.android.utils.FileUtils;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class AndroidStudioInstallation {

  public final TestFileSystem fileSystem;
  private final Path workDir;
  private final LogFile stdout;
  private final LogFile stderr;
  private final LogFile ideaLog;

  // File for storing memory usage statistics. The file is written by calling the `CollectMemoryUsageStatisticsInternalAction` action.
  // After migrating to gRPC API should be removed as a part of b/256132435.
  private final LogFile memoryReportFile;
  private final Path studioDir;
  private final Path vmOptionsPath;
  private final Path configDir;
  private final Path logsDir;

  public static AndroidStudioInstallation fromZip(TestFileSystem testFileSystem) throws IOException {
    return fromZip(testFileSystem, AndroidStudioFlavor.FOR_EXTERNAL_USERS);
  }

  public static AndroidStudioInstallation fromZip(TestFileSystem testFileSystem, AndroidStudioFlavor androidStudioFlavor) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "android-studio");
    System.out.println("workDir: " + workDir);
    String platform = "linux";
    String studioDir = "android-studio";
    if (SystemInfo.isMac) {
      if (SystemInfo.OS_ARCH.equals("aarch64")) {
        platform = "mac_arm";
      } else {
        platform = "mac";
      }
      studioDir = "Android Studio Preview.app/Contents";
    } else if (SystemInfo.isWindows) {
      platform = "win";
      studioDir = "android-studio";
    }

    String zipPath;
    switch (androidStudioFlavor) {
      case FOR_EXTERNAL_USERS:
        zipPath = String.format("tools/adt/idea/studio/android-studio.%s.zip", platform);
        break;
      case ASWB:
        zipPath = String.format("tools/vendor/google/aswb/aswb.%s.zip", platform);
        break;
      default:
        throw new IllegalArgumentException("A valid AndroidStudioFlavor must be passed in. Got: " + androidStudioFlavor);
    }
    Path studioZip = TestUtils.getBinPath(zipPath);
    unzip(studioZip, workDir);

    return new AndroidStudioInstallation(testFileSystem, workDir, workDir.resolve(studioDir), androidStudioFlavor);
  }

  static public AndroidStudioInstallation fromDir(TestFileSystem testFileSystem, Path studioDir) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "android-studio");
    return new AndroidStudioInstallation(testFileSystem, workDir, studioDir, AndroidStudioFlavor.UNKNOWN);
  }

  private AndroidStudioInstallation(TestFileSystem testFileSystem, Path workDir, Path studioDir, AndroidStudioFlavor androidStudioFlavor) throws IOException {
    this.fileSystem = testFileSystem;
    this.workDir = workDir;
    this.studioDir = studioDir;

    logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "logs");
    ideaLog = new LogFile(logsDir.resolve("idea.log"));
    Files.createFile(ideaLog.getPath());
    memoryReportFile = new LogFile(logsDir.resolve("memory_usage_report.log"));
    Files.createFile(memoryReportFile.getPath());
    stdout = new LogFile(logsDir.resolve("stdout.txt"));
    stderr = new LogFile(logsDir.resolve("stderr.txt"));

    vmOptionsPath = workDir.resolve("studio.vmoptions");
    configDir = workDir.resolve("config");
    Files.createDirectories(configDir);

    setConsentGranted(true);
    createVmOptionsFile();
    bundlePlugin(TestUtils.getBinPath("tools/adt/idea/as-driver/asdriver.plugin-studio-sdk.zip"));
    bundlePlugin(TestUtils.getBinPath("prebuilts/studio/intellij-sdk/performanceTesting.zip"));

    System.out.println("AndroidStudioInstallation created with androidStudioFlavor==" + androidStudioFlavor);
  }

  private void bundlePlugin(Path pluginZipPath) throws IOException {
    if (!Files.exists(pluginZipPath)) {
      throw new IllegalStateException("Plugin zip file wasn't found. Path: " + pluginZipPath);
    }

    Path pluginsDir = workDir.resolve("android-studio/plugins");
    Files.createDirectories(pluginsDir);

    unzip(pluginZipPath, pluginsDir);
  }

  private void createVmOptionsFile() throws IOException {
    Path threadingCheckerAgentZip = TestUtils.getBinPath("tools/base/threading-agent/threading_agent.jar");
    if (!Files.exists(threadingCheckerAgentZip)) {
      // Threading agent can be built using 'bazel build //tools/base/threading-agent:threading_agent'
      throw new IllegalStateException("Threading checker agent not found at " + threadingCheckerAgentZip);
    }

    StringBuilder vmOptions = new StringBuilder();
    vmOptions.append(String.format("-javaagent:%s%n", threadingCheckerAgentZip));
    // Need to disable android first run checks, or we get stuck in a modal dialog complaining about lack of web access.
    vmOptions.append(String.format("-Ddisable.android.first.run=true%n"));
    vmOptions.append(String.format("-Dgradle.ide.save.log.to.file=true%n"));
    vmOptions.append(String.format("-Didea.config.path=%s%n", configDir));
    vmOptions.append(String.format("-Didea.plugins.path=%s/plugins%n", configDir));
    vmOptions.append(String.format("-Didea.system.path=%s/system%n", workDir));
    vmOptions.append(String.format("-Djava.io.tmpdir=%s%n", Files.createTempDirectory(workDir, "tmp")));
    // Prevent our crash metrics from going to the production URL
    vmOptions.append(String.format("-Duse.staging.crash.url=true%n"));
    // Work around b/247532990, which is that libnotify.so.4 is missing on our
    // test machines.
    vmOptions.append(String.format("-Dide.libnotify.enabled=false%n"));
    vmOptions.append(String.format("-Didea.log.path=%s%n", logsDir));
    vmOptions.append(String.format("-Duser.home=%s%n", fileSystem.getHome()));
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

    Files.writeString(vmOptionsPath, vmOptions.toString());

    // Handy utility to allow run configurations to force debugging
    if (Sets.newHashSet("true", "1").contains(System.getenv("AS_TEST_DEBUG"))) {
      addDebugVmOption(true);
    }
  }

  public void enableBleak() throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(vmOptionsPath.toFile(), true))) {
      try {
        Path jvmtiAgent = TestUtils.resolveWorkspacePath(
          "_solib_k8/libtools_Sadt_Sidea_Sbleak_Ssrc_Scom_Sandroid_Stools_Sidea_Sbleak_Sagents_Slibjnibleakhelper.so").toRealPath();
        writer.append(String.format("-agentpath:%s%n", jvmtiAgent));
        writer.append(String.format("-Denable.bleak=true%n"));
        writer.append(String.format("-Dbleak.jvmti.enabled=true%n"));
      }
      catch (IOException ignored) {
        throw new IllegalStateException("BLeak JVMTI agent not found");
      }
    }
  }

  private static void unzip(Path zipFile, Path outDir) throws IOException {
    System.out.println("Unzipping...");
    long startTime = System.currentTimeMillis();
    InstallerUtil.unzip(zipFile, outDir, Files.size(zipFile), new FakeProgressIndicator());
    long elapsedTime = System.currentTimeMillis() - startTime;
    System.out.println("Unzipping took " + elapsedTime + "ms");
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
    System.out.println("Creating " + dest);

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
    System.out.println("Creating " + dest);
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
    System.out.println("Creating " + dest);

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

  public Path getWorkDir() {
    return workDir;
  }

  public Path getConfigDir() {
    return configDir;
  }

  public Path getStudioDir() {
    return studioDir;
  }

  public Path getAndroidStudioProjectsDir() {
    return fileSystem.getHome().resolve("AndroidStudioProjects");
  }

  public LogFile getStdout() {
    return stdout;
  }

  public LogFile getStderr() {
    return stderr;
  }

  public LogFile getIdeaLog() {
    return ideaLog;
  }

  public LogFile getMemoryReportFile() {
    return memoryReportFile;
  }

  public void addVmOption(String line) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(vmOptionsPath.toFile(), true))) {
      writer.append(line).append("\n");
    }
  }

  private void addDebugVmOption(boolean suspend) throws IOException {
    // It's easy to forget that debugging was left on, so this emits a warning in that case.
    if (suspend) {
      String hr = "*".repeat(80);
      System.out.println(hr);
      System.out.println("The JDWP suspend option is enabled, meaning the agent's VM will be suspended immediately.\n" +
                         "If you do not attach a debugger to it, your test will time out.");
      System.out.println(hr);
    }
    String s = suspend ? "y" : "n";
    addVmOption("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + s + ",address=localhost:5006\n");
  }

  public AndroidStudio attach() throws IOException, InterruptedException {
    return AndroidStudio.attach(this);
  }

  public AndroidStudio run(Display display) throws IOException, InterruptedException {
    return run(display, new HashMap<>(), new String[] {});
  }

  public AndroidStudio run(Display display, Map<String, String> env) throws IOException, InterruptedException {
    return run(display, env, new String[] {});
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

  public AndroidStudio run(Display display, Map<String, String> env, AndroidProject project, Path sdkDir) throws IOException, InterruptedException {
    Path projectPath = project.install(fileSystem.getRoot());
    project.setSdkDir(sdkDir);
    // Mark that project as trusted
    trustPath(projectPath);
    return run(display, env, new String[]{ projectPath.toString() });
  }

  public AndroidStudio run(Display display, Map<String, String> env, String[] args) throws IOException, InterruptedException {
    return run(display, env, args, false);
  }

  public AndroidStudio run(Display display, Map<String, String> env, String[] args, boolean safeMode) throws IOException, InterruptedException {
    Map<String, String> newEnv = new HashMap<>(env);
    newEnv.put("STUDIO_VM_OPTIONS", vmOptionsPath.toString());
    newEnv.put("HOME", fileSystem.getHome().toString());
    newEnv.put("ANDROID_USER_HOME", this.fileSystem.getAndroidHome().toString());

    return AndroidStudio.run(this, display, newEnv, args, safeMode);
  }

  public AndroidStudio runInSafeMode(Display display) throws IOException, InterruptedException {
    return run(display, new HashMap<>(), new String[] {}, true);
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

  public enum AndroidStudioFlavor {
    // This is the most common version of Android Studio and is what can be found on
    // https://developer.android.com/studio.
    FOR_EXTERNAL_USERS,

    // Android Studio with Blaze.
    ASWB,

    // This indicates that some operation will need to be performed to determine which flavor is
    // being used.
    UNKNOWN,
  }
}
