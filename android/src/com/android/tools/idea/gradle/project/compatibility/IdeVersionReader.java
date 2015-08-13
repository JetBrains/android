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
package com.android.tools.idea.gradle.project.compatibility;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static java.util.Collections.emptyList;

class IdeVersionReader implements ComponentVersionReader {
  @Override
  public boolean appliesTo(@NotNull Module module) {
    return true;
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
  public FileLocation getVersionSource(@NotNull Module module) {
    return null;
  }

  @NotNull
  @Override
  public List<NotificationHyperlink> getQuickFixes(@NotNull Module module,
                                                   @Nullable VersionRange expectedVersion,
                                                   @Nullable FileLocation location) {
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
