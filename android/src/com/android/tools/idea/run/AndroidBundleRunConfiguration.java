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
package com.android.tools.idea.run;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Run Configuration used for running Android Apps locally on a device/emulator via the "bundle" gradle tasks.
 */
public class AndroidBundleRunConfiguration extends AndroidAppRunConfigurationBase {
  public AndroidBundleRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider(@NotNull AndroidFacet facet, @NotNull ApplicationIdProvider applicationIdProvider) {
    if (facet.getConfiguration().getModel() != null && facet.getConfiguration().getModel() instanceof AndroidModuleModel) {
      return new GradleApkProvider(facet, applicationIdProvider, myOutputProvider, false, true);
    }
    return super.getApkProvider(facet, applicationIdProvider);
  }
}
