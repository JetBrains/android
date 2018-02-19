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
package com.android.tools.idea.rendering;

import com.android.tools.idea.configurations.Configuration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class RenderTaskTestUtil {
  private static final String DEFAULT_THEME_STYLE = "@android:style/Theme.Holo";

  @NotNull
  public static RenderTask createRenderTask(@NotNull Module module, @NotNull VirtualFile file) {
    Configuration
      configuration = RenderTestUtil.getConfiguration(module, file, RenderTestUtil.DEFAULT_DEVICE_ID, DEFAULT_THEME_STYLE);
    return RenderTestUtil.createRenderTask(module, file, configuration);
  }
}
