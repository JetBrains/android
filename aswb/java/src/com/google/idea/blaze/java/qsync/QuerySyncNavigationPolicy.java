/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.qsync;

import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.Nullable;

/**
 * Substitutes a workspace source file in place of a decompiled class file for non-project
 * dependencies with sources in the workspace.
 */
public class QuerySyncNavigationPolicy implements ClsCustomNavigationPolicy {

  private static final BoolExperiment ENABLED =
      new BoolExperiment("querysync.navigationpolicy", true);

  @Override
  @Nullable
  public PsiElement getNavigationElement(ClsFileImpl clsFile) {
    Project project = clsFile.getProject();
    if (!Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC) || !ENABLED.getValue()) {
      return null;
    }
    return CachedValuesManager.getCachedValue(
        clsFile,
        () ->
            Result.create(
                new ClassFileJavaSourceFinder(clsFile).findSourceFile(),
                clsFile,
                QuerySyncManager.getInstance(project).getProjectModificationTracker()));
  }
}
