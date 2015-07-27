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

import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AddGradleJetbrainsAnnotationFix extends GradleDependencyFix {
  private final Module myCurrentModule;
  private final PsiReference myReference;
  private final String myClassName;

  public AddGradleJetbrainsAnnotationFix(@NotNull Module currentModule, @NotNull PsiReference reference, @NotNull String className) {
    myCurrentModule = currentModule;
    myReference = reference;
    myClassName = className;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("orderEntry.fix.add.annotations.jar.to.classpath");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,  @Nullable Editor editor,  @Nullable PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed();
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, @Nullable PsiFile file)
    throws IncorrectOperationException {
    VirtualFile location = PsiUtilCore.getVirtualFile(myReference.getElement());
    boolean inTests = location != null && ModuleRootManager.getInstance(myCurrentModule).getFileIndex().isInTestSourceContent(location);
    final Dependency dependency = new Dependency(getDependencyScope(myCurrentModule, inTests), Dependency.Type.EXTERNAL,
                                                 "org.jetbrains:annotations:13.0");

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new WriteCommandAction(project) {
          @Override
          protected void run(@NotNull final Result result) throws Throwable {
            addDependency(myCurrentModule, dependency);
            gradleSyncAndImportClass(myCurrentModule, editor, myReference, new Function<Void, List<PsiClass>>() {
              @Override
              public List<PsiClass> apply(@Nullable Void input) {
                PsiClass aClass =
                  JavaPsiFacade.getInstance(project).findClass(myClassName, GlobalSearchScope.moduleWithLibrariesScope(myCurrentModule));
                return aClass != null ? ImmutableList.of(aClass) : null;
              }
            });
          }
        }.execute();
      }
    });
  }
}
