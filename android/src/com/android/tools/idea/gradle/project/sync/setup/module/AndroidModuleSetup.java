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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.android.*;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class AndroidModuleSetup {
  @NotNull private final AndroidModuleSetupStep[] mySetupSteps;

  public AndroidModuleSetup() {
    this(new AndroidFacetModuleSetupStep(), new SdkModuleSetupStep(), new JdkModuleSetupStep(), new ContentRootsModuleSetupStep(),
         new DependenciesAndroidModuleSetupStep(), new CompilerOutputModuleSetupStep());
  }

  @VisibleForTesting
  AndroidModuleSetup(@NotNull AndroidModuleSetupStep... setupSteps) {
    mySetupSteps = setupSteps;
  }

  public void setUpModule(@NotNull ModuleSetupContext context, @Nullable AndroidModuleModel androidModel, boolean syncSkipped) {
    for (AndroidModuleSetupStep step : mySetupSteps) {
      if (syncSkipped && !step.invokeOnSkippedSync()) {
        continue;
      }
      step.setUpModule(context, androidModel);
    }
  }

  @TestOnly
  @NotNull
  public AndroidModuleSetupStep[] getSetupSteps() {
    return mySetupSteps;
  }
}
