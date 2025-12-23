/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.asdriver.tests.base;

import static com.android.tools.asdriver.tests.MemoryUsageReportProcessorKt.COLLECT_AND_LOG_EXTENDED_MEMORY_REPORTS;
import static com.android.tools.asdriver.tests.MemoryUsageReportProcessorKt.DUMP_HPROF_SNAPSHOT;

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.testlib.AndroidSdk;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.LogFile;
import com.android.tools.testlib.TestFileSystem;
import com.android.tools.testlib.TestLogger;
import com.android.utils.FileUtils;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import org.jetbrains.annotations.NotNull;

public abstract class IdeInstallation<T extends Ide> implements AutoCloseable{

  private final String platformId;

  protected final LogFile ideaLog;
  protected final Path logsDir;
  protected final Path workDir;
  protected final Path studioDir;
  protected final Path configDir;
  protected final Path pluginsDir;
  protected final LogFile stdout;
  protected final LogFile stderr;
  protected final Path vmOptionsPath;
  protected final Path systemDir;
  //Points to a location outside bazel sandbox, used to ensure constant path for any artifact placed inside
  protected final Path tmpDir;

  public final TestFileSystem fileSystem;

  protected IdeInstallation(String platform, TestFileSystem testFileSystem, Path workDir, Path studioDir) throws IOException {
    this.platformId = platform;
    this.fileSystem = testFileSystem;
    this.workDir = workDir;
    this.studioDir = studioDir;
    this.logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "logs");
    this.tmpDir = getTmpDir();
    this.ideaLog = new LogFile(logsDir.resolve("idea.log"));
    Files.createFile(ideaLog.getPath());
    stdout = new LogFile(logsDir.resolve("stdout.txt"));
    stderr = new LogFile(logsDir.resolve("stderr.txt"));

    vmOptionsPath = workDir.resolve("studio.vmoptions");

    configDir = workDir.resolve("config");
    Files.createDirectories(configDir);
    pluginsDir = workDir.resolve("plugins");
    Files.createDirectories(pluginsDir);
    this.systemDir = workDir.resolve("system");
    Files.createDirectories(systemDir);

    createVmOptionsFile();
  }

  @NotNull
  public String getPlatformId() {
    return platformId;
  }


  public static IdeInstallation<? extends Ide> create(TestFileSystem fileSystem) throws IOException {
    String platform = System.getProperty("intellij.plugin.test.platform");
    if ("studio-sdk".equals(platform)) {
      return AndroidStudioInstallation.fromZip(fileSystem);
    }
    // Guess the platform that we are compiled against:
    if (platform != null && platform.startsWith("intellij_")) {
      return IntelliJInstallation.fromZip(platform, fileSystem);
    }
    throw new IllegalStateException("Unknown platform to run " + platform);
  }

  protected static void unzip(Path zipFile, Path outDir) throws IOException {
    TestLogger.log("Unzipping... %s", zipFile);
    long startTime = System.currentTimeMillis();
    InstallerUtil.unzip(zipFile, outDir, Files.size(zipFile), new FakeProgressIndicator());
    long elapsedTime = System.currentTimeMillis() - startTime;
    TestLogger.log("Unzipping took %d ms", elapsedTime);
  }

  public void bundlePlugin(Path pluginZipPath) throws IOException {
    if (!Files.exists(pluginZipPath)) {
      throw new IllegalStateException("Plugin zip file wasn't found. Path: " + pluginZipPath);
    }

    Path pluginsDir = studioDir.resolve("plugins");
    Files.createDirectories(pluginsDir);

    unzip(pluginZipPath, pluginsDir);
  }

  public void installPlugin(Path zip) throws IOException {
    unzip(zip, pluginsDir);
  }

  /** Removes the plugin under the provided folder name, under `plugins` folder under studio directory */
  public void removePlugin(String folderName) {
    deleteDirectoryRecursively(studioDir.resolve("plugins/" + folderName));
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

  public LogFile getIdeaLog() {
    return ideaLog;
  }

  public Path getLogsDir() {
    return logsDir;
  }

  public Path getWorkDir() {
    return workDir;
  }


  public Path getStudioDir() {
    return studioDir;
  }

  public LogFile getStdout() {
    return stdout;
  }

  public LogFile getStderr() {
    return stderr;
  }

  public Path getConfigDir() {
    return configDir;
  }

  public Path getSystemDir() {
    return systemDir;
  }

  private void createVmOptionsFile() throws IOException {
    StringBuilder vmOptions = new StringBuilder();
    createVmOptions(vmOptions);

    Files.writeString(vmOptionsPath, vmOptions.toString());

    // Handy utility to allow run configurations to force debugging
    if (Sets.newHashSet("true", "1").contains(System.getenv("AS_TEST_DEBUG"))) {
      addDebugVmOption(true);
    }
  }

  protected void createVmOptions(StringBuilder vmOptions) throws IOException {
    // On linux, the Ide is not able to resolve the home directory correctly for some builds. So setting it explicitly
    vmOptions.append(String.format("-Didea.home.path=%s%n", studioDir));
    vmOptions.append(String.format("-Didea.config.path=%s%n", configDir));
    vmOptions.append(String.format("-Didea.plugins.path=%s%n", pluginsDir));
    vmOptions.append(String.format("-Didea.system.path=%s%n", systemDir));
    vmOptions.append(String.format("-Djava.io.tmpdir=%s%n", Files.createTempDirectory(workDir, "tmp")));
    // Work around b/247532990, which is that libnotify.so.4 is missing on our
    // test machines.
    vmOptions.append(String.format("-Dide.libnotify.enabled=false%n"));
    vmOptions.append(String.format("-Didea.log.path=%s%n", logsDir));
    vmOptions.append(String.format("-Duser.home=%s%n", fileSystem.getHome()));

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
    // TODO(b/433574645): remove when the RAG index fluctuation is fixed
    vmOptions.append(String.format("-Dgemini.rag.index=none%n"));
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

  public T run(Display display) throws IOException, InterruptedException {
    return run(display, new HashMap<>(), new String[] {});
  }

  public T run(Display display, Map<String, String> env) throws IOException, InterruptedException {
    return run(display, env, new String[] {});
  }

  protected Path setupProject(AndroidProject project, Path sdkDir) throws IOException {
    Path projectPath = project.install(fileSystem.getRoot());
    project.setSdkDir(sdkDir);
    // Mark that project as trusted
    trustPath(projectPath);
    return projectPath;
  }

  protected Path setupProjectAtTmpDir(AndroidProject project) throws IOException {
    Path projectPath = project.installAtTmpDir(tmpDir);
    project.setSdkDir(getSdkDir());
    // Mark that project as trusted
    trustPath(projectPath);
    return projectPath;
  }

  public T run(Display display, Map<String, String> env, AndroidProject project, Path sdkDir) throws IOException, InterruptedException {
    Path projectPath = setupProject(project, sdkDir);
    return run(display, env, new String[]{ projectPath.toString() });
  }

  public T runIdeFromTmpDir(Display display, Map<String, String> env, AndroidProject project) throws IOException, InterruptedException {
    Path projectPath = setupProjectAtTmpDir(project);
    Path jdkDir = getJdkDir();
    String javaHome = jdkDir.toAbsolutePath().toString();
    env.put("GRADLE_LOCAL_JAVA_HOME", javaHome);
    env.put("JAVA_HOME", javaHome);
    env.put("STUDIO_GRADLE_JDK", javaHome);
    env.put("STUDIO_JDK", javaHome);
    addVmOption("-Dgradle.jvm=$javaHome");
    return run(display, env, new String[]{ projectPath.toString() });
  }

  public T run(Display display, Map<String, String> userEnv, String[] args) throws IOException, InterruptedException {
    Map<String, String> env = new HashMap<>(userEnv);
    env.put(vmOptionEnvName(), vmOptionsPath.toString());
    env.put("HOME", fileSystem.getHome().toString());
    // This is only needed for Android Studio, but does no harm to others
    env.put("ANDROID_USER_HOME", fileSystem.getAndroidHome().toString());

    Path workDir = getWorkDir();

    ArrayList<String> command = new ArrayList<>(args.length + 1);
    command.add(getExecutable());

    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().clear();

    for (Map.Entry<String, String> entry : env.entrySet()) {
      pb.environment().put(entry.getKey(), entry.getValue());
    }
    if (display.getDisplay() != null) {
      pb.environment().put("DISPLAY", display.getDisplay());
    }
    pb.environment().put("XDG_DATA_HOME", workDir.resolve("data").toString());
    String shell = System.getenv("SHELL");
    if (shell != null && !shell.isEmpty()) {
      pb.environment().put("SHELL", shell);
    }

    TestLogger.log("Starting IDE %s", platformId);
    getStdout().reset();
    getStderr().reset();
    pb.redirectOutput(getStdout().getPath().toFile());
    pb.redirectError(getStderr().getPath().toFile());
    // We execute it and let the process instance go, as it reflects
    // the shell process, not the idea one.
    pb.start();
    // Now we attach to the real one from the logs
    return createAndAttach();
  }

  /**
   * Run from an APK project, which is different from an {@link AndroidProject} in that it doesn't
   * have Gradle files or a {@code local.properties} file.
   *
   * Running from the "File" → "Profile or Debug APK" flow would also work, but that requires more
   * automation to set up.
   */
  public T runFromExistingProject(Display display, HashMap<String, String> env, String path) throws IOException, InterruptedException {
    Path project = TestUtils.resolveWorkspacePath(path);
    Path targetProject = Files.createTempDirectory(fileSystem.getRoot(), "project");
    FileUtils.copyDirectory(project.toFile(), targetProject.toFile());

    trustPath(targetProject);
    return run(display, env, new String[]{ targetProject.toString() });
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

  public void enableBleak() throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(vmOptionsPath.toFile(), true))) {
      try {
        String agentName;
        if (SystemInfo.isMac) {
          agentName = "libjnibleakhelper.dylib";
        } else if (SystemInfo.isLinux) {
          agentName = "libjnibleakhelper.so";
        } else {
          throw new IllegalStateException("BLeak is not supported on " + System.getProperty("os.name"));
        }
        Path jvmtiAgent = TestUtils.resolveWorkspacePath(
          "tools/adt/idea/bleak/native/" + agentName).toRealPath();
        writer.append(String.format("-agentpath:%s%n", jvmtiAgent));
        writer.append(String.format("-Denable.bleak=true%n"));
        writer.append(String.format("-Dbleak.jvmti.enabled=true%n"));
        writer.append(String.format("-Didea.disposer.debug=on%n"));
        // BLeak requires more memory since it's keeping track of various live objects
        writer.append(String.format("-Xmx4G%n"));
      }
      catch (IOException ignored) {
        throw new IllegalStateException("BLeak JVMTI agent not found");
      }
    }
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

  /**
   * Writes a file to disk that will make the platform think that the user has already interacted
   * with the "Send usage statistics" dialog. This process is faster than automating the
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

  /**
   * Checks to see if Android Studio failed to start because the JDWP address was already in use.
   * This method will throw an exception in the test process rather than the developer having to
   * check Android Studio's stderr itself. I.e. this method is purely for developer convenience
   * when testing locally.
   */
  static protected void checkForJdwpError(IdeInstallation<? extends Ide> installation) {
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

  static protected int waitForDriverPid(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver started on pid: (\\d+).*", null, true, 120, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  static protected int waitForDriverServer(LogFile reader) throws IOException, InterruptedException {
    Matcher matcher = reader.waitForMatchingLine(".*STDOUT - as-driver server listening at: (\\d+).*", null, true, 30, TimeUnit.SECONDS);
    return Integer.parseInt(matcher.group(1));
  }

  protected T createAndAttach() throws IOException, InterruptedException {
    T studio = attach();
    studio.startCapturingScreenshotsOnWindows();
    return studio;
  }

  public static Path getTmpDir() {
    String tmpDir = "/tmp/android-studio-test-artifacts";
    if (SystemInfo.isMac) {
      tmpDir = "/private/tmp/android-studio-test-artifacts";
    } else if (SystemInfo.isWindows) {
      tmpDir = "C:\\Temp\\android-studio-test-artifacts";
    }
    return Path.of(tmpDir);
  }

  public Path getSdkDir() {
    return this.tmpDir.resolve("sdk");
  }

  public Path getJdkDir() {
    return this.tmpDir.resolve("jdk");
  }

  public void setupTmpDir() throws IOException {
    FileUtils.deleteRecursivelyIfExists(tmpDir.toFile());
    Files.createDirectories(tmpDir);
    setupSdkAtTmpDir();
    setupJdkAtTmpDir();
  }

  public void setupSdkAtTmpDir() throws IOException {
    Path sdkDir = getSdkDir();
    Files.createDirectories(sdkDir);
    Path prebuiltSdk = TestUtils.resolveWorkspacePath(TestUtils.getRelativeSdk());
    FileUtils.copyDirectory(prebuiltSdk.toFile(), sdkDir.toFile());
  }

  public void setupJdkAtTmpDir() throws IOException {
    Path jdkDir = getJdkDir();
    Files.createDirectories(jdkDir);
    FileUtils.copyDirectory(TestUtils.getJava21Jdk().toFile(), jdkDir.toFile());
  }

  public void copySystemDir(Path projectArtifactsPath) throws IOException {
    FileUtils.copyDirectory(TestUtils.getBinPath(projectArtifactsPath.resolve("system").toString()).toFile(), getSystemDir().toFile());
  }

  public void copyConfigDir(Path projectArtifactsPath) throws IOException {
    FileUtils.copyDirectory(TestUtils.getBinPath(projectArtifactsPath.resolve("config").toString()).toFile(), getConfigDir().toFile());
  }

  abstract public T attach() throws IOException, InterruptedException;

  abstract protected String vmOptionEnvName();

  abstract protected String getExecutable();
}