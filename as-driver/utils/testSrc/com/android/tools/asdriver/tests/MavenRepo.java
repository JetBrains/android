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

import com.android.testutils.RepoLinker;
import com.android.test.testutils.TestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class MavenRepo {
  private final String manifest;

  public MavenRepo(String manifest) {
    this.manifest = manifest;
  }

  public void install(Path tempDir, AndroidStudioInstallation install, HashMap<String, String> env) throws Exception {

    Path offlineRepoManifest = TestUtils.resolveWorkspacePathUnchecked(manifest);
    Path repoDir;
    if (!Files.exists(offlineRepoManifest)) {
      // If running in the IDE linking the repo is very hard as the paths are ../maven and that does not exist in
      // the source tree, so we approximate by using the prebuilt repo. We could do better by analyzing each file
      // individually and determine if they are in bazel-bin or in prebuilts
      repoDir = TestUtils.resolveWorkspacePath("prebuilts/tools/common/m2/repository");
    } else {
      repoDir = tempDir.resolve("offline-repo");
      System.out.printf("Linking offline repo %s to %s%n", offlineRepoManifest, repoDir);

      RepoLinker linker = new RepoLinker();
      List<String> artifacts = Files.readAllLines(offlineRepoManifest);
      linker.link(repoDir, artifacts);
    }

    env.put("STUDIO_CUSTOM_REPO", repoDir.toString());
    // Configure studio to read STUDIO_CUSTOM_REPO as a development offline repository, and use it for new projects and upgrade assistant
    install.addVmOption("-Dgradle.ide.development.offline.repos=true");
    // Also add that repository with a Gradle init script to every gradle invocation, both build and sync, to allow opening of existing
    // projects without requiring network access.
    install.addVmOption("-Dgradle.ide.inject.repos.with.init.script=true");
  }
}
