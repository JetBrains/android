/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.compiler;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gradle configuration settings.
 */
@State(
  name = "GradleCompilerConfiguration",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gradleCompiler.xml", scheme = StorageScheme.DIRECTORY_BASED)
  }
)
public class GradleCompilerConfiguration implements PersistentStateComponent<GradleCompilerConfiguration> {
  public int MAX_HEAP_SIZE = SystemInfo.is32Bit ? 512 : 1024;
  public int MAX_PERM_GEN_SIZE = SystemInfo.is32Bit ? 128 : 256;

  @Override
  @Nullable
  public GradleCompilerConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(GradleCompilerConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static GradleCompilerConfiguration getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleCompilerConfiguration.class);
  }
}
