/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.compatibility.version;

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.Collections.emptyList;

class IdeVersionReader implements ComponentVersionReader {
  @NotNull private final String myIdeName;

  IdeVersionReader(@NotNull String ideName) {
    myIdeName = ideName;
  }

  @Override
  public boolean appliesTo(@NotNull Module module) {
    return myIdeName.equals(getComponentName());
  }

  @Nullable
  @Override
  public String getComponentVersion(@NotNull Module module) {
    // Strict version: 1.3.0.2
    // Full version: 1.3 Preview 3
    return ApplicationInfo.getInstance().getStrictVersion();
  }

  @Nullable
  @Override
  public PositionInFile getVersionSource(@NotNull Module module) {
    return null;
  }

  @NotNull
  @Override
  public List<NotificationHyperlink> getQuickFixes(@NotNull Module module,
                                                   @Nullable VersionRange expectedVersion,
                                                   @Nullable PositionInFile location) {
    return emptyList();
  }

  @Override
  public boolean isProjectLevel() {
    return true;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return ApplicationInfo.getInstance().getVersionName();
  }
}
