/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.Set;
import java.util.function.Predicate;

/** Causes C files to become prefetched. */
public class CPrefetchFileSource implements PrefetchFileSource {

  private static final BoolExperiment prefetchAllCppSources =
      new BoolExperiment("prefetch.all.cpp.sources", true);

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C)
        || !prefetchAllCppSources.getValue()) {
      return;
    }
    // Prefetch all non-project CPP header files encountered during sync
    Predicate<ArtifactLocation> shouldPrefetch =
        location -> {
          if (!location.isSource() || location.isExternal()) {
            return false;
          }
          WorkspacePath path = WorkspacePath.createIfValid(location.getRelativePath());
          if (path == null || importRoots.containsWorkspacePath(path)) {
            return false;
          }
          String extension = FileUtil.getExtension(path.relativePath());
          return CFileExtensions.HEADER_EXTENSIONS.contains(extension);
        };
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    for (TargetIdeInfo target : blazeProjectData.getTargetMap().targets()) {
      if (target.getcIdeInfo() == null) {
        continue;
      }
      target.getSources().stream().filter(shouldPrefetch).map(decoder::decode).forEach(files::add);
    }
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.<String>builder()
        .addAll(CFileExtensions.SOURCE_EXTENSIONS)
        .addAll(CFileExtensions.HEADER_EXTENSIONS)
        .build();
  }
}
