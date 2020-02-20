/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.mlkit.MlkitNames;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generates config required for light model class construction.
 */
public class MlModelClassGenerator {

  private static final Logger LOG = Logger.getInstance(MlModelClassGenerator.class);

  @Nullable
  public static LightModelClassConfig generateLightModelClass(@NotNull Module module, @NotNull MlModelMetadata modelMetadata) {
    String modulePackageName = ProjectSystemUtil.getModuleSystem(module).getPackageName();
    if (modulePackageName == null) {
      LOG.warn("No valid packageName for module:" + module.getName());
      return null;
    }
    return new LightModelClassConfig(modelMetadata, modulePackageName + MlkitNames.PACKAGE_SUFFIX);
  }
}
