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
package com.google.idea.blaze.android.sync;

import com.google.idea.blaze.android.sync.importer.AllowlistFilter;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.android.sync.importer.problems.GeneratedResourceRetentionFilter;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Collection;

/** Augments the java sync process with Android support. */
public class BlazeAndroidJavaSyncAugmenter implements BlazeJavaSyncAugmenter {
  // b/194967269: Attaching resource jars is not required anymore. This experiment disables
  // the addition, and if we don't see any issues in a month or so, it can be deleted.
  private static final BoolExperiment attachResourceJars =
      new BoolExperiment("aswb.attach.resource.jars.for.nores.libs", false);

  @Override
  public void addJarsForSourceTarget(
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ProjectViewSet projectViewSet,
      TargetIdeInfo target,
      Collection<BlazeJarLibrary> jars,
      Collection<BlazeJarLibrary> genJars) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.ANDROID)) {
      return;
    }
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo == null) {
      return;
    }
    LibraryArtifact idlJar = androidIdeInfo.getIdlJar();
    if (idlJar != null) {
      genJars.add(new BlazeJarLibrary(idlJar, target.getKey()));
    }

    if (!attachResourceJars.getValue()) {
      return;
    }

    if (androidIdeInfo.generateResourceClass()
        && !BlazeAndroidWorkspaceImporter.containsSourcesOrAllowedGeneratedResources(
            androidIdeInfo,
            new AllowlistFilter(
                BlazeImportUtil.getAllowedGenResourcePaths(projectViewSet),
                GeneratedResourceRetentionFilter.getFilter()))) {
      // Add blaze's output unless it's a top level rule.
      // In these cases the resource jar contains the entire
      // transitive closure of R classes. It's unlikely this is wanted to resolve in the IDE.
      boolean discardResourceJar =
          target.getKind().getRuleType() == RuleType.TEST
              || target.getKind().getRuleType() == RuleType.BINARY;
      if (!discardResourceJar) {
        LibraryArtifact resourceJar = androidIdeInfo.getResourceJar();
        if (resourceJar != null) {
          jars.add(new BlazeJarLibrary(resourceJar, target.getKey()));
        }
        Logger.getInstance(BlazeAndroidJavaSyncAugmenter.class)
            .warn("Adding resource jar for target " + target.getKey().getLabel());
      }
    }
  }
}
