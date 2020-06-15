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

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Generates config required for light model class construction.
 */
public class MlModelClassGenerator {

  public static LightModelClassConfig generateLightModelClass(@NotNull Module module, @NotNull MlModelMetadata modelMetadata) {
    // TODO(b/144867508): placeholder for now, implement it with parsing the given model file.
    return new LightModelClassConfig(modelMetadata, "com.google.mlkit.auto");
  }
}
