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

import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import org.jetbrains.annotations.NotNull;

/**
 * Stores all values needed to generate a specific {@link LightModelClass}.
 */
public class LightModelClassConfig {
  public final MlModelMetadata myModelMetadata;
  public final String myPackageName;

  public LightModelClassConfig(@NotNull MlModelMetadata modelMetadata, @NotNull String packageName) {
    myModelMetadata = modelMetadata;
    myPackageName = packageName;
  }
}
