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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.google.common.base.Function;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.tools.idea.gradle.dsl.parser.CommonConfigurationNames.*;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

abstract class AbstractGradleDependencyFix extends AbstractGradleAwareFix {
  @NotNull final Module myModule;
  @NotNull final PsiReference myReference;

  AbstractGradleDependencyFix(@NotNull Module module, @NotNull PsiReference reference) {
    myModule = module;
    myReference = reference;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,  @Nullable Editor editor,  @Nullable PsiFile file) {
    return !project.isDisposed() && !myModule.isDisposed();
  }

  static boolean isTestScope(@NotNull Module module, @NotNull PsiReference reference) {
    VirtualFile location = getVirtualFile(reference.getElement());
    return isTestScope(module, location);
  }

  static boolean isTestScope(@NotNull Module module, @Nullable VirtualFile location) {
    return location != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(location);
  }

  static void addDependency(@NotNull Module module, @NotNull String configurationName, @NotNull String compactNotation) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      buildModel.addExternalDependency(configurationName, compactNotation);
      registerUndoAction(module.getProject());
    }
  }

  @NotNull
  static String getConfigurationName(@NotNull Module module, boolean testScope) {
    if (testScope) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(androidFacet);
        String configurationName = TEST_COMPILE;
        if (androidModel != null && ARTIFACT_ANDROID_TEST.equals(androidModel.getSelectedTestArtifactName())) {
          configurationName = ANDROID_TEST_COMPILE;
        }
        return configurationName;
      }
    }
    return COMPILE;

  }

  /**
   * After modifying the dependencies of the gradle file, trigger gradle sync and then try to add import statement to the source file.
   *
   * @param project the project in which the quick fix is invoked.
   * @param editor the editor in which the quick fix is invoked.
   * @param reference the PSI element that can't be resolved initially.
   * @param getTargetClasses the callback to find resolved classes for the reference after sync is done.
   */
  protected static void gradleSyncAndImportClass(@NotNull final Project project,
                                                 @Nullable final Editor editor,
                                                 @Nullable final PsiReference reference,
                                                 @Nullable final Function<Void, List<PsiClass>> getTargetClasses) {
    GradleProjectImporter.getInstance().requestProjectSync(project, false /* Do not generate source */, new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull final Project project) {
        if (editor != null && reference != null) {
          DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
            @Override
            public void run() {
              List<PsiClass> targetClasses = null;
              if (getTargetClasses != null) {
                targetClasses = getTargetClasses.apply(null);
              }
              if (targetClasses != null) {
                new AddImportAction(project, reference, editor, targetClasses.toArray(new PsiClass[targetClasses.size()])).execute();
              }
            }
          });
        }
      }
    });
  }
}
