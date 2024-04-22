/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project import logic used by UI elements
 */
public final class ProjectImportUtil {
  private static final String[] GRADLE_SUPPORTED_FILES = new String[] {
    SdkConstants.FN_BUILD_GRADLE,
    SdkConstants.FN_BUILD_GRADLE_KTS,
    SdkConstants.FN_SETTINGS_GRADLE,
    SdkConstants.FN_SETTINGS_GRADLE_KTS,
  };

  private ProjectImportUtil() {
    // Do not instantiate
  }

  public static VirtualFile findGradleTarget(@NotNull VirtualFile file) {
    if (StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.get()) {
      String[] declarativeFiles = new String[]{SdkConstants.FN_BUILD_GRADLE_DECLARATIVE, SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE};
      return findMatch(file, ArrayUtils.addAll(GRADLE_SUPPORTED_FILES, declarativeFiles));
    }
    return findMatch(file, GRADLE_SUPPORTED_FILES);
  }

  @Nullable
  private static VirtualFile findMatch(@NotNull VirtualFile location, @NotNull String... validNames) {
    if (location.isDirectory()) {
      for (VirtualFile child : location.getChildren()) {
        for (String name : validNames) {
          if (name.equals(child.getName())) {
            return child;
          }
        }
      }
    }
    else {
      for (String name : validNames) {
        if (name.equals(location.getName())) {
          return location;
        }
      }
    }
    return null;
  }
}
