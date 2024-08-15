/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.run.debug;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.libraries.JarCache;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Objects;
import java.util.Optional;

/** Provides the absolute path of the coroutines debugging library artifact. */
public class BazelKotlinxCoroutinesDebuggingLibProvider
    implements KotlinxCoroutinesDebuggingLibProvider {

  @Override
  public Optional<String> getKotlinxCoroutinesDebuggingLib(
      ArtifactLocation coroutinesLibArtifact, BlazeCommandRunConfiguration config) {
    Project project = config.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      notify("Cannot view coroutines debugging panel: project needs to be synced.");
      return Optional.empty();
    }

    File libFile =
        JarCache.getInstance(project)
            .getCachedSourceJar(
                blazeProjectData.getArtifactLocationDecoder(), coroutinesLibArtifact);
    if (libFile == null) {
      notify(
          "Cannot view coroutines debugging panel: %s jar file cannot be found.",
          coroutinesLibArtifact.getRelativePath());
      return Optional.empty();
    }

    return Optional.of(libFile.getAbsolutePath());
  }

  @Override
  public boolean isApplicable(Project project) {
    return Objects.equals(BuildSystemName.Bazel, Blaze.getBuildSystemName(project));
  }
}
