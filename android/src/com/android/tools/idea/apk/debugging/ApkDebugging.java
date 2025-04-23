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
package com.android.tools.idea.apk.debugging;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

public final class ApkDebugging {
  @NotNull public static final ProjectSystemId SYSTEM_ID = new ProjectSystemId("APK Import", "APK Project");

  @VisibleForTesting
  public static final String APK_DEBUGGING_PROPERTY = "com.android.ide.apk.debugging";

  private static final boolean APK_DEBUGGING_ENABLED = SystemProperties.getBooleanProperty("apk.importer.enabled", false);

  private ApkDebugging() {
  }

  public static boolean isEnabled() {
    return true;
  }

  public static void markAsApkDebuggingProject(@NotNull Project project) {
    PropertiesComponent.getInstance(project).setValue(APK_DEBUGGING_PROPERTY, true);
  }

  public static boolean isMarkedAsApkDebuggingProject(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(APK_DEBUGGING_PROPERTY, false);
  }
}
