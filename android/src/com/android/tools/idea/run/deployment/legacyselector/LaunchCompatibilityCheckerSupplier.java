/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.LaunchCompatibilityCheckerImpl;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class LaunchCompatibilityCheckerSupplier implements Supplier<LaunchCompatibilityChecker> {
  private final @NotNull Project myProject;
  @NotNull private final Function<Module, AndroidFacet> myFacetGetter;
  private volatile boolean myCheckerComputed;
  private @Nullable volatile LaunchCompatibilityChecker myChecker;

  LaunchCompatibilityCheckerSupplier(@NotNull Project project, @NotNull Function<Module, AndroidFacet> facetGetter) {
    myProject = project;
    myFacetGetter = facetGetter;
  }

  @WorkerThread
  @Override
  public LaunchCompatibilityChecker get() {
    if (!myCheckerComputed) {
      myChecker = computeChecker();
      myCheckerComputed = true;
    }
    return myChecker;
  }

  @WorkerThread
  private @Nullable LaunchCompatibilityChecker computeChecker() {
    RunnerAndConfigurationSettings configurationAndSettings = RunManager.getInstance(myProject).getSelectedConfiguration();
    if (configurationAndSettings == null) {
      return null;
    }

    Object configuration = configurationAndSettings.getConfiguration();

    if (!(configuration instanceof ModuleBasedConfiguration)) {
      return null;
    }

    Module module = ((ModuleBasedConfiguration<?, ?>)configuration).getConfigurationModule().getModule();

    if (module == null) {
      return null;
    }

    AndroidFacet facet = myFacetGetter.apply(module);

    if (facet == null || facet.isDisposed()) {
      return null;
    }

    if (DumbService.isDumb(myProject)) {
      return null;
    }

    return LaunchCompatibilityCheckerImpl.create(facet, null, null);
  }
}
