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

import com.android.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Provides common utility methods. */
public class MlkitUtils {

  private MlkitUtils() {
  }

  public static boolean isMlModelFileInAssetsFolder(@NotNull VirtualFile file) {
    // TODO(b/144867508): support other model types.
    // TODO(b/146357353): revisit the way to check if the file belongs to assets folder.
    return file.getFileType() == TfliteModelFileType.INSTANCE
           && file.getParent() != null
           && file.getParent().getName().equals(SdkConstants.FD_ASSETS);
  }

  /** Returns the light model classes auto-generated for ML model files in the assets folder across all project modules. */
  public List<LightModelClass> getLightModelClassList(@NotNull Project project) {
    List<LightModelClass> lightModelClassList = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      lightModelClassList.addAll(MlkitModuleService.getInstance(module).getLightModelClassList());
    }
    return lightModelClassList;
  }
}
