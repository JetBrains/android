/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.common.BaseSetup;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidVariantChangeModuleSetup extends BaseSetup<AndroidModuleSetupStep, AndroidModuleModel> {
  public AndroidVariantChangeModuleSetup() {
    this(new ContentRootsModuleSetupStep(), new DependenciesAndroidModuleSetupStep(), new CompilerOutputModuleSetupStep());
  }

  @VisibleForTesting
  public AndroidVariantChangeModuleSetup(@NotNull AndroidModuleSetupStep... setupSteps) {
    super(setupSteps);
  }

  @Override
  protected boolean shouldRunSyncStep(@NotNull AndroidModuleSetupStep step, boolean syncSkipped) {
    return step.invokeOnBuildVariantChange();
  }

  public void setUpModule(@NotNull ModuleSetupContext context, @Nullable AndroidModuleModel model) {
    super.setUpModule(context, model, false);
  }
}
