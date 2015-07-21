/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.quickfix;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

abstract class GradleDependencyFix implements IntentionAction, LocalQuickFix, HighPriorityAction {
  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @NotNull
  public String getName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  protected static void addDependency(@NotNull Module module, @NotNull Dependency dependency) {
    GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);

    if (gradleBuildFile == null) {
      return;
    }

    List<BuildFileStatement> dependencies = Lists.newArrayList(gradleBuildFile.getDependencies());
    dependencies.add(dependency);

    gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
  }

  @NotNull
  protected static Dependency.Scope getDependencyScope(@NotNull Module module, boolean test) {
    Dependency.Scope testScope = Dependency.Scope.TEST_COMPILE;
    if (test) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        IdeaAndroidProject androidProject = androidFacet.getIdeaAndroidProject();
        if (androidProject != null && AndroidProject.ARTIFACT_ANDROID_TEST.equals(androidProject.getSelectedTestArtifactName())) {
          testScope = Dependency.Scope.ANDROID_TEST_COMPILE;
        }
      }
    }
    return test ? testScope : Dependency.Scope.COMPILE;
  }
}