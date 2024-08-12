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
package com.google.idea.blaze.android.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.libraries.LintCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Objects;

/** {@inheritDoc} Collecting lint rule jars from {@code AarLibrary} */
public class AndroidLintCollector implements LintCollector {
  public static final FeatureRolloutExperiment lintEnabled =
      new FeatureRolloutExperiment("blaze.android.libraries.lint.enabled");

  @Override
  public ImmutableList<File> collectLintJars(Project project, BlazeProjectData blazeProjectData) {
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    return BlazeLibraryCollector.getLibraries(
            ProjectViewManager.getInstance(project).getProjectViewSet(), blazeProjectData)
        .stream()
        .filter(library -> library instanceof AarLibrary)
        .map(library -> ((AarLibrary) library).getLintRuleJar(project, artifactLocationDecoder))
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @Override
  public boolean isEnabled() {
    return lintEnabled.isEnabled();
  }
}
