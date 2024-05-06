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
package com.android.tools.idea.memorysettings;

import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.DEFAULT_HEAP_SIZE_IN_MB;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;

import com.android.tools.idea.projectsystem.GradleToken;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.util.Arrays;

public class MemorySettingsGradleToken implements MemorySettingsToken<GradleProjectSystem>, GradleToken {
  public static final int LARGE_MODULE_COUNT = 100;
  public static final int XLARGE_MODULE_COUNT = 200;
  @Override
  public BuildSystemComponent createBuildSystemComponent(GradleProjectSystem projectSystem) {
    return new GradleComponent();
  }

  @Override
  public int getRecommendedXmxFor(GradleProjectSystem projectSystem) {
    Project project = projectSystem.getProject();
    long numberOfModules = Arrays.stream(ModuleManager.getInstance(project).getModules()).filter(ModuleSystemUtil::isHolderModule).count();
    if (numberOfModules >= XLARGE_MODULE_COUNT) {
      return XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    } else if (numberOfModules >= LARGE_MODULE_COUNT) {
      return LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    } else {
      return DEFAULT_HEAP_SIZE_IN_MB;
    }

  }
}
