/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

final class ConstraintLayoutFeature {
  private ConstraintLayoutFeature() {
  }

  static boolean isSupportedInSdkManager(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    // see https://code.google.com/p/android/issues/detail?id=360563
    return model == null /* 'null' means this is a brand-new project */ || model.getFeatures().isConstraintLayoutSdkLocationSupported();
  }
}
