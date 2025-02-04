/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.filecache.LocalArtifactCache;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class RuntimeArtifactCacheImpl implements RuntimeArtifactCache {
  private final LocalArtifactCache cache;

  private RuntimeArtifactCacheImpl(Project project) throws IOException {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    Preconditions.checkNotNull(
        importSettings, "Could not get directory for project '%s'", project.getName());

    Path folder = BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve("apkCache");
    Files.createDirectories(folder);
    cache = new LocalArtifactCache(project, "APK Cache", folder);
  }

  @Override
  public ImmutableList<Path> fetchArtifacts(
      Label target, List<? extends OutputArtifact> artifacts, BlazeContext context) {
    cache.putAll(artifacts, context, true);
    return artifacts.stream().map(a -> cache.get(a)).collect(toImmutableList());
  }
}
