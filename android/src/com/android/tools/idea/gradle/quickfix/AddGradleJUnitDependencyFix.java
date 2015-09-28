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

import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.search.GlobalSearchScope.moduleWithLibrariesScope;

/**
 * Quickfix to add JUnit dependency to gradle.build file and sync the project.
 */
public class AddGradleJUnitDependencyFix extends AbstractGradleDependencyFix {
  @NotNull private final String myClassName;
  private final boolean myIsJunit4;

  public AddGradleJUnitDependencyFix(@NotNull Module module, @NotNull PsiReference reference, @NotNull String className, boolean isJunit4) {
    super(module, reference);
    myClassName = className;
    myIsJunit4 = isJunit4;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("orderEntry.fix.add.junit.jar.to.classpath");
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable Editor editor, @Nullable PsiFile file) {
    ExternalDependencySpec newDependency = new ExternalDependencySpec("junit", "junit", "3.8.1");
    if (myIsJunit4) {
      newDependency.version = "4.12";
    }
    String configurationName = getConfigurationName(myModule, isTestScope(myModule, myReference));

    addDependencyAndSync(configurationName, newDependency, new Computable<PsiClass[]>() {
      @Override
      public PsiClass[] compute() {
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myClassName, moduleWithLibrariesScope(myModule));
        return aClass != null ? new PsiClass[]{aClass} : null;
      }
    }, editor);
  }
}
