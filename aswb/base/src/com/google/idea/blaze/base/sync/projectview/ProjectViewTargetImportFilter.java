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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.Tags;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ExcludeTargetSection;
import com.google.idea.blaze.base.projectview.section.sections.ImportTargetOutputSection;
import com.google.idea.blaze.base.settings.BuildSystemName;
import java.util.Set;

/** Filters rules into source/library depending on the project view. */
public class ProjectViewTargetImportFilter {
  private final ImportRoots importRoots;
  private final Set<Label> importTargetOutputs;
  private final Set<Label> excludedTargets;

  public ProjectViewTargetImportFilter(
      BuildSystemName buildSystemName, WorkspaceRoot workspaceRoot, ProjectViewSet projectViewSet) {
    this.importRoots =
        ImportRoots.builder(workspaceRoot, buildSystemName).add(projectViewSet).build();
    this.importTargetOutputs =
        Sets.newHashSet(projectViewSet.listItems(ImportTargetOutputSection.KEY));
    this.excludedTargets = Sets.newHashSet(projectViewSet.listItems(ExcludeTargetSection.KEY));
  }

  public boolean isSourceTarget(TargetIdeInfo target) {
    return importRoots.importAsSource(target.getKey().getLabel()) && !importTargetOutput(target);
  }

  private boolean importTargetOutput(TargetIdeInfo target) {
    return target.getTags().contains(Tags.TARGET_TAG_IMPORT_TARGET_OUTPUT)
        || target.getTags().contains(Tags.TARGET_TAG_IMPORT_AS_LIBRARY_LEGACY)
        || importTargetOutputs.contains(target.getKey().getLabel());
  }

  public boolean excludeTarget(TargetIdeInfo target) {
    return excludedTargets.contains(target.getKey().getLabel())
        || target.getTags().contains(Tags.TARGET_TAG_PROVIDED_BY_SDK)
        || target.getTags().contains(Tags.TARGET_TAG_EXCLUDE_TARGET);
  }
}
