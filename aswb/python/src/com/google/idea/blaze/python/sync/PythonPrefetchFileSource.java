/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.sync;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.PyIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Causes python files to become prefetched. */
public class PythonPrefetchFileSource implements PrefetchFileSource, OutputsProvider {

  private static final BoolExperiment prefetchAllPythonSources =
      new BoolExperiment("prefetch.all.py.sources", true);

  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageSettings.isLanguageActive(LanguageClass.PYTHON);
  }

  @Override
  public Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target) {
    return target.getPyIdeInfo() != null ? target.getPyIdeInfo().getSources() : ImmutableList.of();
  }

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.PYTHON)
        || !prefetchAllPythonSources.getValue()) {
      return;
    }
    // Prefetch all non-project python source files found during sync
    Predicate<ArtifactLocation> shouldPrefetch =
        location -> {
          if (location.isGenerated()) {
            return true;
          }
          WorkspacePath path = WorkspacePath.createIfValid(location.getRelativePath());
          return path != null && !importRoots.containsWorkspacePath(path);
        };
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    List<File> sourceFiles =
        blazeProjectData.getTargetMap().targets().stream()
            .filter(t -> t.getPyIdeInfo() != null)
            .map(TargetIdeInfo::getPyIdeInfo)
            .map(PyIdeInfo::getSources)
            .flatMap(Collection::stream)
            .filter(shouldPrefetch)
            .map(a -> OutputArtifactResolver.resolve(project, decoder, a))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    files.addAll(sourceFiles);
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("py", "pyw", "pyi");
  }
}
