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
package com.android.tools.idea.gradle.project.sync.setup.module.ndk;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.common.BaseSetup;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkVariantChangeModuleSetup extends BaseSetup<NdkModuleSetupStep, NdkModuleModel> {

  public NdkVariantChangeModuleSetup() {
    super(new ContentRootModuleSetupStep());
  }

  @VisibleForTesting
  public NdkVariantChangeModuleSetup(@NotNull NdkModuleSetupStep... setupSteps) {
    super(setupSteps);
  }

  @Override
  protected boolean shouldRunSyncStep(@NotNull NdkModuleSetupStep step, boolean syncSkipped) {
    return step.invokeOnBuildVariantChange();
  }

  public void setUpModule(@NotNull ModuleSetupContext context, @Nullable NdkModuleModel model) {
    super.setUpModule(context, model, false);
  }
}
