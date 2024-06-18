/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run;


import static com.android.AndroidProjectTypes.PROJECT_TYPE_TEST;

import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is required to support proper work in module per source set mode where we keep using holder modules
 * in the configuration but need to delegate scope checks to the proper sub-module.
 */
public class AndroidRunConfigurationModule extends JavaRunConfigurationModule {

  private final boolean myIsTestConfiguration;

  public AndroidRunConfigurationModule(@NotNull Project project, boolean isTestConfiguration) {
    super(project, false);
    myIsTestConfiguration = isTestConfiguration;
  }

  @Override
  public @NotNull GlobalSearchScope getSearchScope() {
    Module module = getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (myIsTestConfiguration && facet != null && facet.getProperties().PROJECT_TYPE != PROJECT_TYPE_TEST) {
        module = ModuleSystemUtil.getAndroidTestModule(module);
      } else {
        module = ModuleSystemUtil.getMainModule(module);
      }
      if (module != null) {
        return GlobalSearchScope.moduleWithDependenciesScope(module);
      }
    }

    return GlobalSearchScope.projectScope(getProject());
  }

  public @Nullable Module getAndroidTestModule() {
    return getModule() == null ? null : ModuleSystemUtil.getAndroidTestModule(getModule());
  }
}
