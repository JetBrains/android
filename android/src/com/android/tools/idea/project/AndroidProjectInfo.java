/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public class AndroidProjectInfo {
  private static final Key<Boolean> HAS_NATIVE_MODULES = Key.create("HAS_NATIVE_MODULES");
  private final @NotNull Project myProject;

  private AndroidProjectInfo(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static AndroidProjectInfo get(@NotNull Project project) {
    return new AndroidProjectInfo(project);
  }

  public void setHasNativeModules(boolean hasNativeModules) {
    myProject.putUserData(HAS_NATIVE_MODULES, hasNativeModules);
  }

  public boolean hasNativeModules() {
    Boolean boolRes = myProject.getUserData(HAS_NATIVE_MODULES);
    return (boolRes != null) ? boolRes.booleanValue() : false;
  }
}
