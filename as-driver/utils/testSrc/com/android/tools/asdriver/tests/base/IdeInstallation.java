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

import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.android.tools.testlib.Display;
import com.android.tools.testlib.LogFile;
import com.android.tools.testlib.TestFileSystem;
import com.android.tools.testlib.TestLogger;
import com.google.common.collect.Sets;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public abstract class IdeInstallation<T extends Ide> {

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

  public final TestFileSystem fileSystem;

  protected IdeInstallation(String platform, TestFileSystem testFileSystem, Path workDir, Path studioDir) throws IOException {
    this.platformId = platform;
    this.fileSystem = testFileSystem;
    this.workDir = workDir;
    this.studioDir = studioDir;
    this.logsDir = Files.createTempDirectory(TestUtils.getTestOutputDir(), "logs");
    this.ideaLog = new LogFile(logsDir.resolve("idea.log"));
    Files.createFile(ideaLog.getPath());
    stdout = new LogFile(logsDir.resolve("stdout.txt"));
    stderr = new LogFile(logsDir.resolve("stderr.txt"));

    vmOptionsPath = workDir.resolve("studio.vmoptions");

    configDir = workDir.resolve("config");
    Files.createDirectories(configDir);
    pluginsDir = workDir.resolve("plugins");
    Files.createDirectories(pluginsDir);

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
    vmOptions.append(String.format("-Didea.config.path=%s%n", configDir));
    vmOptions.append(String.format("-Didea.plugins.path=%s%n", pluginsDir));
    vmOptions.append(String.format("-Didea.system.path=%s/system%n", workDir));
    vmOptions.append(String.format("-Djava.io.tmpdir=%s%n", Files.createTempDirectory(workDir, "tmp")));
    // Work around b/247532990, which is that libnotify.so.4 is missing on our
    // test machines.
    vmOptions.append(String.format("-Dide.libnotify.enabled=false%n"));
    vmOptions.append(String.format("-Didea.log.path=%s%n", logsDir));
    vmOptions.append(String.format("-Duser.home=%s%n", fileSystem.getHome()));
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

  public void installPlugin(Path zip) throws IOException {
    unzip(zip, pluginsDir);
  }

  public T run(Display display) throws IOException, InterruptedException {
    return run(display, new HashMap<>(), new String[] {});
  }

  public T run(Display display, Map<String, String> env) throws IOException, InterruptedException {
    return run(display, env, new String[] {});
  }

  abstract protected String getExecutable();

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

  public void enableBleak() throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(vmOptionsPath.toFile(), true))) {
      try {
        Path jvmtiAgent = TestUtils.resolveWorkspacePath(
          "tools/adt/idea/bleak/native/libjnibleakhelper.so").toRealPath();
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

  public Path getConfigDir() {
    return configDir;
  }

  abstract protected T createAndAttach() throws IOException, InterruptedException;

  abstract protected String vmOptionEnvName();
}
