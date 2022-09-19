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
import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
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

  private final TestFileSystem fileSystem;
  private final Path workDir;
  private final LogFile stdout;
  private final LogFile stderr;
  private final LogFile ideaLog;
  private final Path studioDir;
  private final Path vmOptionsPath;
  private final Path configDir;
  private final Path logsDir;

  public static AndroidStudioInstallation fromZip(TestFileSystem testFileSystem) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "android-studio");
    System.out.println("workDir: " + workDir);
    String platform = "linux";
    String studioDir = "android-studio";
    if (SystemInfo.isMac) {
      platform = "mac";
      studioDir = "Android Studio Preview.app/Contents";
    } else if (SystemInfo.isWindows) {
      platform = "win";
      studioDir = "android-studio";
    }

    Path studioZip = TestUtils.getBinPath(String.format("tools/adt/idea/studio/android-studio.%s.zip", platform));
    unzip(studioZip, workDir);

    return new AndroidStudioInstallation(testFileSystem, workDir, workDir.resolve(studioDir));
  }

  static public AndroidStudioInstallation fromDir(TestFileSystem testFileSystem, Path studioDir) throws IOException {
    Path workDir = Files.createTempDirectory(testFileSystem.getRoot(), "android-studio");
    return new AndroidStudioInstallation(testFileSystem, workDir, studioDir);
  }

  private AndroidStudioInstallation(TestFileSystem testFileSystem, Path workDir, Path studioDir) throws IOException {
    this.fileSystem = testFileSystem;
    this.workDir = workDir;
    this.studioDir = studioDir;

    logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "logs");
    ideaLog = new LogFile(logsDir.resolve("idea.log"));
    Files.createFile(ideaLog.getPath());
    stdout = new LogFile(logsDir.resolve("stdout.txt"));
    stderr = new LogFile(logsDir.resolve("stderr.txt"));

    vmOptionsPath = workDir.resolve("studio.vmoptions");
    configDir = workDir.resolve("config");
    Files.createDirectories(configDir);

    setConsentGranted(true);
    createVmOptionsFile();
  }

  private void createVmOptionsFile() throws IOException {
    Path agentZip = TestUtils.getBinPath("tools/adt/idea/as-driver/as_driver_deploy.jar");
    if (!Files.exists(agentZip)) {
      throw new IllegalStateException("agent not found at " + agentZip);
    }

    Path threadingCheckerAgentZip = TestUtils.getBinPath("tools/base/threading-agent/threading_agent.jar");
    if (!Files.exists(threadingCheckerAgentZip)) {
      // Threading agent can be built using 'bazel build //tools/base/threading-agent:threading_agent'
      throw new IllegalStateException("Threading checker agent not found at " + threadingCheckerAgentZip);
    }

    String vmOptions = String.format("-javaagent:%s%n", agentZip) +
                       String.format("-javaagent:%s%n", threadingCheckerAgentZip) +
                       // Need to disable android first run checks, or we get stuck in a modal dialog complaining about lack of web access.
                       String.format("-Ddisable.android.first.run=true%n") +
                       String.format("-Dgradle.ide.save.log.to.file=true%n") +
                       String.format("-Didea.config.path=%s%n", configDir) +
                       String.format("-Didea.plugins.path=%s/plugins%n", configDir) +
                       String.format("-Didea.system.path=%s/system%n", workDir) +
                       // Prevent our crash metrics from going to the production URL
                       String.format("-Duse.staging.crash.url=true%n") +
                       // Work around b/247532990, which is that libnotify.so.4 is missing on our
                       // test machines.
                       String.format("-Dide.libnotify.enabled=false%n") +
                       String.format("-Didea.log.path=%s%n", logsDir) +
                       String.format("-Duser.home=%s%n", fileSystem.getHome());
    Files.write(vmOptionsPath, vmOptions.getBytes(StandardCharsets.UTF_8));

    // Handy utility to allow run configurations to force debugging
    if (Sets.newHashSet("true", "1").contains(System.getenv("AS_TEST_DEBUG"))) {
      addDebugVmOption(true);
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
   * Emits the agent's stdout and stderr logs to the console. When running a test locally, this can
   * be helpful for viewing the logs without having to suspend any processes; without this, you
   * would have to manually locate the randomly created temporary directories holding the logs.
   */
  public void debugEmitLogs() {
    try {
      stdout.printContents();
      stderr.printContents();
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
   * Prevents a notification about {@code .pro} files and "Shrinker Config" from popping up. This
   * notification occurs as a result of two plugins trying to register the {@code .pro} file type.
   *
   * @see com.intellij.openapi.fileTypes.impl.ConflictingFileTypeMappingTracker#resolveConflict
   */
  public void preventProguardNotification() throws IOException {
    Path filetypePaths = configDir.resolve("options/filetypes.xml");

    if (filetypePaths.toFile().exists()) {
      throw new IllegalStateException(
        String.format("%s already exists, which means this method should be changed to merge with it rather than overwriting it.",
                      filetypePaths));
    }

    Files.createDirectories(filetypePaths.getParent());
    String filetypeContents = String.format(
      "<application>%n" +
      "  <component name=\"FileTypeManager\" version=\"18\">%n" +
      "    <extensionMap>%n" +
      "      <removed_mapping ext=\"pro\" type=\"DeviceSpecFile\" />%n" +
      "      <mapping ext=\"pro\" type=\"Shrinker Config File\" />%n" +
      "    </extensionMap>%n" +
      "  </component>%n" +
      "</application>");
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
    Path dest = workDir.resolve("config/options/androidStudioFirstRun.xml");
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

  public Path getStudioDir() {
    return studioDir;
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

  public AndroidStudio run(Display display, Map<String, String> env, AndroidProject project, Path sdkDir) throws IOException, InterruptedException {
    Path projectPath = project.install(fileSystem.getRoot());
    project.setSdkDir(sdkDir);
    // Mark that project as trusted
    trustPath(projectPath);
    return run(display, env, new String[]{ projectPath.toString() });
  }

  public AndroidStudio run(Display display, Map<String, String> env, String[] args) throws IOException, InterruptedException {
    Map<String, String> newEnv = new HashMap<>(env);
    newEnv.put("STUDIO_VM_OPTIONS", vmOptionsPath.toString());
    newEnv.put("HOME", fileSystem.getHome().toString());
    newEnv.put("ANDROID_USER_HOME", this.fileSystem.getAndroidHome().toString());

    return AndroidStudio.run(this, display, newEnv, args);
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
}
