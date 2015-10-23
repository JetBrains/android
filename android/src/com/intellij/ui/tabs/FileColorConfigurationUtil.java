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
package com.intellij.ui.tabs;

import com.android.tools.idea.gradle.testing.TestArtifactCustomScopeProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// TODO make FileColorConfiguration public so we don't need put this class under com.intellij.ui.tabs package
public class FileColorConfigurationUtil {
  public static void createAndroidTestFileColorConfigurationIfNotExist(@NotNull Project project) {
    createFileColorConfigurationIfNotExist(project, TestArtifactCustomScopeProvider.AndroidTestsScope.NAME, "Blue");
  }

  private static void createFileColorConfigurationIfNotExist(@NotNull Project project, @NotNull String scopeName,
                                                             @NotNull String colorName) {
    FileColorManagerImpl fileColorManager =
      (FileColorManagerImpl)FileColorManager.getInstance(project);

    List<FileColorConfiguration> colorConfigurations = fileColorManager.getApplicationLevelConfigurations();
    for (FileColorConfiguration configuration : colorConfigurations) {
      if (scopeName.equals(configuration.getScopeName())) {
        return;
      }
    }

    // Android test scope need to be put in first in order to override the normal test scope
    colorConfigurations.add(0, new FileColorConfiguration(scopeName, colorName));
  }
}
