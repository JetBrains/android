/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidClassBrowserBase extends BrowseModuleValueActionListener {
  protected final ConfigurationModuleSelector myModuleSelector;
  protected final String myDialogTitle;
  protected final boolean myIncludeLibraryClasses;

  public AndroidClassBrowserBase(@NotNull Project project,
                                 @NotNull ConfigurationModuleSelector moduleSelector,
                                 @NotNull String dialogTitle,
                                 boolean includeLibraryClasses) {
    super(project);
    myIncludeLibraryClasses = includeLibraryClasses;
    myDialogTitle = dialogTitle;
    myModuleSelector = moduleSelector;
  }

  @Nullable
  protected PsiClass findClass(final String className) {
    return myModuleSelector.findClass(className);
  }

  @Override
  protected String showDialog() {
    Project project = getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    Module module = myModuleSelector.getModule();
    if (module == null) {
      Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), CommonBundle.getErrorTitle());
      return null;
    }
    GlobalSearchScope scope =
      myIncludeLibraryClasses ? module.getModuleWithDependenciesAndLibrariesScope(true) : module.getModuleWithDependenciesScope();
    PsiClass initialSelection = facade.findClass(getText(), scope);
    TreeClassChooser chooser = createTreeClassChooser(project, scope, initialSelection, new ClassFilter() {
      @Override
      public boolean isAccepted(PsiClass aClass) {
        if (aClass.isInterface()) return false;
        final PsiModifierList modifierList = aClass.getModifierList();
        return modifierList == null || !modifierList.hasModifierProperty(PsiModifier.ABSTRACT);
      }
    });
    if (chooser == null) return null;
    chooser.showDialog();
    PsiClass selClass = chooser.getSelected();
    return selClass != null ? selClass.getQualifiedName() : null;
  }

  @Nullable
  protected abstract TreeClassChooser createTreeClassChooser(@NotNull Project project,
                                                             @NotNull GlobalSearchScope scope,
                                                             @Nullable PsiClass initialSelection,
                                                             @NotNull ClassFilter classFilter);
}
