/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidTestClassBrowser extends AndroidClassBrowserBase {

  public AndroidTestClassBrowser(@NotNull Project project,
                                 @NotNull ConfigurationModuleSelector moduleSelector,
                                 @NotNull String dialogTitle,
                                 boolean includeLibraryClasses) {
    super(project, moduleSelector, dialogTitle, includeLibraryClasses);
  }

  @Nullable
  @Override
  protected TreeClassChooser createTreeClassChooser(@NotNull Project project,
                                                    @NotNull GlobalSearchScope scope,
                                                    @Nullable PsiClass initialSelection, @NotNull final ClassFilter classFilter) {
    return TreeClassChooserFactory.getInstance(project).createNoInnerClassesScopeChooser(myDialogTitle, scope, new ClassFilter() {
      @Override
      public boolean isAccepted(PsiClass aClass) {
        return classFilter.isAccepted(aClass) && JUnitUtil.isTestClass(aClass);
      }
    }, initialSelection);
  }
}
