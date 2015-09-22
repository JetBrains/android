/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run.testing;

import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

public class AndroidInheritingClassVisibilityChecker extends AndroidClassVisibilityCheckerBase {
  private final Project myProject;
  private final String myBaseClassName;

  public AndroidInheritingClassVisibilityChecker(@NotNull Project project,
                                                 @NotNull ConfigurationModuleSelector moduleSelector,
                                                 @NotNull String baseClassName) {
    super(moduleSelector);
    myProject = project;
    myBaseClassName = baseClassName;
  }


  @Override
  protected boolean isVisible(@NotNull Module module, @NotNull PsiClass aClass) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    final PsiClass baseClass = facade.findClass(myBaseClassName, module.getModuleWithDependenciesAndLibrariesScope(true));
    return baseClass != null && (aClass).isInheritor(baseClass, true);
  }
}
