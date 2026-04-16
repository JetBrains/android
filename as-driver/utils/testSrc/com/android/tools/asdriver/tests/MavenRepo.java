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

import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.repository.util.InstallerUtil;
import com.android.testutils.RepoLinker;
import com.android.testutils.TestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

/**
 * Manages an offline Maven repository in integration tests.
 *
 * <p>The repository can be defined either by an artifact manifest file or a ZIP archive.
 *
 * <p>It handles the configuration of the {@code STUDIO_CUSTOM_REPO} environment variable and adds
 * necessary VM options to the Android Studio installation to use the offline repository.
 */
public class MavenRepo {
  private final String path;
  private boolean skipInitScriptInjection = false;

  public MavenRepo(String path) {
    this.path = path;
  }

  /** Disables the injection of the repository into Gradle via an init script. */
  public MavenRepo withoutInitScript() {
    this.skipInitScriptInjection = true;
    return this;
  }

  /**
   * Installs the repository by configuring the environment and populating the repository directory
   * if necessary.
   *
   * @param tempDir A temporary directory available for the installation.
   * @param install The Android Studio installation to configure.
   * @param env The environment variables map to update.
   */
  public void install(Path tempDir, AndroidStudioInstallation install, HashMap<String, String> env)
      throws Exception {
    Path resolvedPath = TestUtils.resolveWorkspacePathUnchecked(path);
    Path repoDir;
    if (!Files.exists(resolvedPath)) {
      // If running in the IDE linking the repo is very hard as the paths are ../maven and that does not exist in
      // the source tree, so we approximate by using the prebuilt repo. We could do better by analyzing each file
      // individually and determine if they are in bazel-bin or in prebuilts
      repoDir = TestUtils.resolveWorkspacePath("prebuilts/tools/common/m2/repository");
    } else {
      repoDir = tempDir.resolve("offline-repo");

      if (!Files.exists(repoDir)) {
        Files.createDirectories(repoDir);
      }

      if (path.endsWith(".zip")) {
        System.out.println("Unzipping offline repo " + resolvedPath + " to " + repoDir);
        InstallerUtil.unzip(resolvedPath, repoDir, Files.size(resolvedPath), new ProgressIndicatorAdapter() {});
      } else {
        System.out.printf("Linking offline repo %s to %s%n", resolvedPath, repoDir);

        RepoLinker linker = new RepoLinker();
        List<String> artifacts = Files.readAllLines(resolvedPath);
        linker.link(repoDir, artifacts, TestUtils::resolveWorkspacePathUnchecked);
      }
    }

    env.put("STUDIO_CUSTOM_REPO", repoDir.toString());
    // Configure studio to read STUDIO_CUSTOM_REPO as a development offline repository, and use it for new projects and upgrade assistant
    install.addVmOption("-Dgradle.ide.development.offline.repos=true");
    if (!skipInitScriptInjection) {
      // Also add that repository with a Gradle init script to every gradle invocation, both build and sync, to allow opening of existing
      // projects without requiring network access.
      install.addVmOption("-Dgradle.ide.inject.repos.with.init.script=true");
    }
  }
}
