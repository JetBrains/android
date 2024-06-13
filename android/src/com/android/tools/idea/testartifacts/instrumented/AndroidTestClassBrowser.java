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

import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import javax.swing.JComponent;
import org.jetbrains.android.util.AndroidTreeClassChooserFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidTestClassBrowser<T extends JComponent> extends AndroidClassBrowserBase<T> {

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
    return AndroidTreeClassChooserFactory.INSTANCE.createNoInnerClassesScopeChooser(
      project, myDialogTitle, scope, aClass -> classFilter.isAccepted(aClass) && JUnitUtil.isTestClass(aClass), initialSelection);
  }

  @Override
  protected @Nullable Module getModuleForScope() {
    Module module = super.getModuleForScope();
    return module == null ? null : ModuleSystemUtil.getAndroidTestModule(module);
  }
}
