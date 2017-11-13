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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ModulesByGradlePath {
  @NotNull private final Map<String, Module> myValues = new HashMap<>();

  public void addModule(@NotNull Module module, @NotNull String gradlePath) {
    myValues.put(gradlePath, module);
  }

  @Nullable
  public Module findModuleByGradlePath(@NotNull String gradlePath) {
    return myValues.get(gradlePath);
  }

  @Override
  public String toString() {
    return myValues.toString();
  }

  public static class Factory {
    @NotNull
    public ModulesByGradlePath create() {
      return new ModulesByGradlePath();
    }
  }
}
