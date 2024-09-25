/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.idea.blaze.android.manifest;

import static com.google.idea.blaze.android.manifest.ManifestParser.parseManifestFromInputStream;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.annotation.Nullable;

/** Obtains and caches {@link ManifestParser.ParsedManifest}. */
public class ParsedManifestService {
  private final Map<File, ManifestParser.ParsedManifest> manifestFileToParsedManifests =
      Maps.newHashMap();

  public static ParsedManifestService getInstance(Project project) {
    return project.getService(ParsedManifestService.class);
  }

  /**
   * Returns parsed manifest from the given manifest file. Returns null if the manifest is invalid.
   *
   * <p>An invalid manifest is anything that could not be parsed by the parser, such as a malformed
   * manifest file. This method must be thread safe as it's invoked from concurrent background
   * threads.
   *
   * @throws IOException only when an IO error occurs. Errors related to malformed manifests are
   *     indicated by returning null.
   */
  @Nullable
  public synchronized ManifestParser.ParsedManifest getParsedManifest(File file)
      throws IOException {
    if (!manifestFileToParsedManifests.containsKey(file)) {
      try (InputStream inputStream = new FileInputStream(file)) {
        ManifestParser.ParsedManifest parsedManifest = parseManifestFromInputStream(inputStream);
        if (parsedManifest == null) {
          return null;
        }
        manifestFileToParsedManifests.put(file, parsedManifest);
      }
    }

    return manifestFileToParsedManifests.get(file);
  }

  public void invalidateCachedManifest(File manifestFile) {
    manifestFileToParsedManifests.keySet().remove(manifestFile);
  }

  static class ClearManifestParser implements SyncListener {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        ImmutableSet<Integer> buildIds,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      getInstance(project).manifestFileToParsedManifests.clear();
    }
  }

  private ParsedManifestService() {}
}
