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
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Matches source files to test targets, if the source file is present in the test target's 'srcs'
 * list. Only looks for exact matches.
 */
public class TestTargetSourcesHeuristic implements TestTargetHeuristic {

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    Optional<ImmutableList<ArtifactLocation>> sources = target.getSources();
    if (!sources.isPresent()) {
      return false;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return false;
    }

    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    for (ArtifactLocation src : sources.get()) {
      if (Objects.equals(decoder.resolveSource(src), sourceFile)) {
        return true;
      }
    }
    return false;
  }
}
