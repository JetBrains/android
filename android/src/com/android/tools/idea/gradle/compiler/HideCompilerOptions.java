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

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.compiler.options.CompilerOptionsFilter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Hides options in the "Compiler" preference page that are either redundant or not applicable to Gradle-based Android projects:
 * <ul>
 *   <li>"Add @NotNull assertions"</li>
 *   <li>"Use external build" and children</li>
 * </ul>
 */
public class HideCompilerOptions implements CompilerOptionsFilter {
  @Override
  public boolean isAvailable(@NotNull Setting setting, @NotNull Project project) {
    if (!Projects.requiresAndroidModel(project)) {
      return true;
    }
    return Setting.EXTERNAL_BUILD != setting && Setting.ADD_NOT_NULL_ASSERTIONS != setting;
  }
}
