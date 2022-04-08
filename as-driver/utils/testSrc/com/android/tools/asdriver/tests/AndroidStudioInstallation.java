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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class AndroidStudioInstallation implements AutoCloseable {

  private final Path workDir;
  private final Path stdout;
  private final Path stderr;
  private final Path ideaLog;
  private final Path vmOptionsPath;

  public AndroidStudioInstallation() throws IOException {
    //TODO: Set up as data to run in bazel
    Path studioZip = getBinPath("tools/adt/idea/studio/android-studio.linux.zip");
    workDir = Files.createTempDirectory("android-studio");
    unzip(studioZip, workDir);

    Path agentZip = AndroidStudioInstallation.getBinPath("tools/adt/idea/as-driver/as_driver_deploy.jar");
    if (!Files.exists(agentZip)) {
      throw new IllegalStateException("agent not found at " + agentZip);
    }
    stdout = workDir.resolve("stdout.txt");
    stderr = workDir.resolve("stderr.txt");
    ideaLog = workDir.resolve("system/log/idea.log");
    vmOptionsPath = workDir.resolve("studio.vmoptions");
    Path configDir = workDir.resolve("config");

    String vmOptions =
      String.format("-javaagent:%s\n", agentZip) +
      String.format("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5006\n") +
      String.format("-Didea.config.path=%s/config\n", workDir) +
      String.format("-Didea.plugins.path=%s/config/plugins\n", workDir) +
      String.format("-Didea.system.path=%s/system\n", workDir) +
      String.format("-Didea.log.path=%s/system/log\n", workDir) +
      String.format("-Duser.home=%s/home\n", workDir);
    Files.write(vmOptionsPath, vmOptions.getBytes(StandardCharsets.UTF_8));
    Files.createDirectories(configDir);
  }

  private void unzip(Path zipFile, Path outDir) throws IOException {
    System.out.println("Unzipping...");
    InstallerUtil.unzip(zipFile, outDir, Files.size(zipFile), new FakeProgressIndicator());
    System.out.println("Done");
  }

  public Path getWorkDir() {
    return workDir;
  }

  public Path getStderr() {
    return stderr;
  }

  public Path getStdout() {
    return stdout;
  }

  public Path getVmOptionsPath() {
    return vmOptionsPath;
  }

  public Path getIdeaLog() {
    return ideaLog;
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
    System.out.println("Deleting...");
    try (Stream<Path> walk = Files.walk(workDir)) {
      walk.sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
    }
    System.out.println("Done");
  }

  public AndroidStudio run() throws IOException, InterruptedException {
    return new AndroidStudio(this);
  }
}
