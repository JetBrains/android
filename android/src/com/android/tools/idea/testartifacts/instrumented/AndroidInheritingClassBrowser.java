/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.idea.testartifacts.instrumented;

import com.intellij.CommonBundle;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import javax.swing.JComponent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidTreeClassChooserFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidInheritingClassBrowser<T extends JComponent> extends AndroidClassBrowserBase<T> {
  private final String myBaseClassName;

  public AndroidInheritingClassBrowser(@NotNull Project project,
                                       @NotNull ConfigurationModuleSelector moduleSelector,
                                       @NotNull String baseClassName,
                                       @NotNull String dialogTitle,
                                       boolean includeLibraryClasses) {
    super(project, moduleSelector, dialogTitle, includeLibraryClasses);
    myBaseClassName = baseClassName;
  }

  @Override
  protected TreeClassChooser createTreeClassChooser(@NotNull Project project,
                                                    @NotNull GlobalSearchScope scope,
                                                    @Nullable PsiClass initialSelection,
                                                    @NotNull final ClassFilter classFilter) {
    final PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(myBaseClassName, ProjectScope.getAllScope(project));

    if (baseClass == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("cant.find.class.error", myBaseClassName), CommonBundle.getErrorTitle());
      return null;
    }
    return AndroidTreeClassChooserFactory.INSTANCE.createInheritanceClassChooser(
      project, myDialogTitle, scope, baseClass, initialSelection, aClass -> {
        if (aClass.getManager().areElementsEquivalent(aClass, baseClass)) {
          return false;
        }
        return classFilter.isAccepted(aClass);
      });
  }
}
