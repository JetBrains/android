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
package com.android.tools.idea.wizard;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static utility methods used by the New Project/New Module wizards
 */
public class WizardUtils {

  /**
   * Remove spaces, switch to lower case, and remove any invalid characters. If the resulting name
   * conflicts with an existing module, append a number to the end to make a unique name.
   */
  @NotNull
  public static String computeModuleName(@NotNull String appName, @Nullable Project project) {
    String moduleName = appName.toLowerCase().replaceAll(WizardConstants.INVALID_FILENAME_CHARS, "");
    moduleName = moduleName.replaceAll("\\s", "");

    if (!isUniqueModuleName(moduleName, project)) {
      int i = 2;
      while (!isUniqueModuleName(moduleName + Integer.toString(i), project)) {
        i++;
      }
      moduleName += Integer.toString(i);
    }
    return moduleName;
  }

  /**
   * @return true if the given module name is unique inside the given project. Returns true if the given
   * project is null.
   */
  public static boolean isUniqueModuleName(@NotNull String moduleName, @Nullable Project project) {
    if (project == null) {
      return true;
    }
    // Check our modules
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module m : moduleManager.getModules()) {
      if (m.getName().equalsIgnoreCase(moduleName)) {
        return false;
      }
    }
    return true;
  }
}
