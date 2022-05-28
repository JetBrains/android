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

import static org.apache.commons.io.file.PathUtils.copyDirectory;

import com.android.SdkConstants;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class AndroidStudioInstallation implements AutoCloseable {

  private final Path workDir;
  private final Path stdout;
  private final Path stderr;
  private final Path ideaLog;
  private final Path vmOptionsPath;

  /**
   * A temporary directory that is deleted on exit. This is to be used for arbitrary test tasks,
   * e.g. unzipping .jar files.
   */
  private final Path e2eTempDir;

  public AndroidStudioInstallation() throws IOException {
    e2eTempDir = Files.createTempDirectory("e2e-framework");
    String overrideDir = getOverrideWorkDir();

    if (overrideDir == null) {
      //TODO: Set up as data to run in bazel
      workDir = Files.createTempDirectory("android-studio");
      System.out.println("workDir: " + workDir);
      Path studioZip = getBinPath("tools/adt/idea/studio/android-studio.linux.zip");
      unzip(studioZip, workDir);
    } else {
      workDir = Path.of(overrideDir);
      System.out.println("workDir (overridden by environment variable): " + workDir + ". Skipping unzip process.");
    }

    stdout = workDir.resolve("stdout.txt");
    stderr = workDir.resolve("stderr.txt");

    ideaLog = workDir.resolve("system/log/idea.log");
    vmOptionsPath = workDir.resolve("studio.vmoptions");
    Path configDir = workDir.resolve("config");
    Files.createDirectories(configDir);

    setConsentGranted(true);
    createVmOptionsFile();
  }

  private void createVmOptionsFile() throws IOException {
    Path agentZip = AndroidStudioInstallation.getBinPath("tools/adt/idea/as-driver/as_driver_deploy.jar");
    if (!Files.exists(agentZip)) {
      throw new IllegalStateException("agent not found at " + agentZip);
    }

    String vmOptions = String.format("-javaagent:%s\n", agentZip) +
                       String.format("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5006\n") +
                       String.format("-Didea.config.path=%s/config\n", workDir) +
                       String.format("-Didea.plugins.path=%s/config/plugins\n", workDir) +
                       String.format("-Didea.system.path=%s/system\n", workDir) +
                       String.format("-Didea.log.path=%s/system/log\n", workDir) +
                       String.format("-Duser.home=%s/home\n", workDir);
    Files.write(vmOptionsPath, vmOptions.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * For quicker development, the working directory can be overridden to point to an already-
   * unzipped copy of Android Studio (on slower machines, unzipping can take ~20 seconds).
   */
  private String getOverrideWorkDir() {
    return System.getenv("OVERRIDE_WORK_DIR");
  }

  private void unzip(Path zipFile, Path outDir) throws IOException {
    System.out.println("Unzipping...");
    long startTime = System.currentTimeMillis();
    InstallerUtil.unzip(zipFile, outDir, Files.size(zipFile), new FakeProgressIndicator());
    long elapsedTime = System.currentTimeMillis() - startTime;
    System.out.println("Unzipping took " + elapsedTime + "ms");
  }

  /**
   * Emits the agent's stdout and stderr logs to the console. At the time of writing, this is
   * useful because the logs aren't captured anywhere permanent, so this is potentially the only
   * way to diagnose test failures until we come up with something more permanent.
   *
   * TODO(b/234144552): save off the log files rather than emitting them to stdout.
   */
  public void emitLogs() {
    try {
      String stdoutContents = Files.readString(stdout);
      String stderrContents = Files.readString(stderr);
      System.out.println("Emitting logs from the agent:");
      System.out.println("===STDOUT===");
      System.out.println(stdoutContents);
      System.out.println("===STDERR===");
      System.out.println(stderrContents);
      System.out.println("===END===");
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
    Files.createDirectories(consentOptions.getParent());
    Files.writeString(consentOptions, combinedString);
  }

  /**
   * Copies an already-built SDK to a path where Android Studio will find it.
   *
   * TODO(b/234069426): change this function to <i>point</i> at the SDK rather than copying it
   * @param androidPlatform A string like "android-32".
   * @throws IOException If there's a problem copying the files.
   */
  public void copySdk(String androidPlatform) throws IOException {
    Path sdkSource = TestUtils.getSdk().resolve(SdkConstants.FD_PLATFORMS).resolve(androidPlatform);
    Path dest = workDir.resolve("home/Android/Sdk").resolve(SdkConstants.FD_PLATFORMS).resolve(androidPlatform);
    Files.createDirectories(dest.getParent());
    System.out.println("Copying " + sdkSource + " to " + dest);
    copyDirectory(sdkSource, dest, StandardCopyOption.REPLACE_EXISTING);
  }

  /**
   * Modifies the build number within an Android Studio installation so that it will think it's a
   * different build.
   * @throws IOException
   */
  public void setBuildNumber(String buildNumber) throws IOException {
    Path resourcesJar = workDir.resolve("android-studio/lib/resources.jar");
    Path tempDir = e2eTempDir.resolve("modify_resources_jar");
    Path unzippedDir = tempDir.resolve("unzipped");
    Files.createDirectories(unzippedDir);
    InstallerUtil.unzip(resourcesJar, unzippedDir, Files.size(resourcesJar), new FakeProgressIndicator());

    Path appInfoXml = unzippedDir.resolve("idea/AndroidStudioApplicationInfo.xml");
    Charset charset = StandardCharsets.UTF_8;
    String xmlContent = Files.readString(appInfoXml, charset);

    // This is more brittle than using XPath, but it's sufficient for now.
    xmlContent = xmlContent.replaceAll("(.*<build number=\")(.*?)(\".*)", String.format("$1%s$3", buildNumber));
    Files.write(appInfoXml, xmlContent.getBytes(charset));
    Path newJarPath = tempDir.resolve("resources.jar");
    TestUtils.zipDirectory(unzippedDir, newJarPath);

    Files.delete(resourcesJar);
    Files.copy(newJarPath, resourcesJar);

    Files.write(workDir.resolve("android-studio/build.txt"), buildNumber.getBytes(charset));
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

  public Path getWorkDir() {
    return workDir;
  }

  public Path getE2eTempDir() {
    return e2eTempDir;
  }

  public Path getStderr() {
    return stderr;
  }

  public Path getStdout() {
    return stdout;
  }

  public Path getIdeaLog() {
    return ideaLog;
  }

  public void addVmOption(String line) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(vmOptionsPath.toFile(), true))) {
      writer.append(line).append("\n");
    }
  }

  public void addDebugVmOption(boolean suspend) throws IOException {
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

  static public Path getBinPath(String bin) {
    Path path = TestUtils.resolveWorkspacePathUnchecked(bin);
    if (!Files.exists(path)) {
      // running from IJ
      path = TestUtils.resolveWorkspacePathUnchecked("bazel-bin/" + bin);
    }
    return path;
  }

  @Override
  public void close() throws Exception {
    // If the user provided their own directory, then keep the contents around
    // to make future runs faster.
    if (getOverrideWorkDir() != null) {
      System.out.println("Skipping deletion of working directory due to override");
      return;
    }
    deleteDirectory(workDir);
    deleteDirectory(e2eTempDir);
  }

  /**
   * Recursively deletes the given directory.
   */
  private void deleteDirectory(Path dir) throws IOException {
    System.out.println("Deleting directory " + dir);
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
    }
    System.out.println("Done deleting " + dir);
  }

  public AndroidStudio run() throws IOException, InterruptedException {
    return run(new HashMap<>());
  }

  public AndroidStudio run(Map<String, String> env) throws IOException, InterruptedException {
    Map<String, String> newEnv = new HashMap<>(env);
    newEnv.put("STUDIO_VM_OPTIONS", vmOptionsPath.toString());
    return new AndroidStudio(this, newEnv);
  }
}
