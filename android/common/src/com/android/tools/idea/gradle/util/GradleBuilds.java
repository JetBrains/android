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
package com.android.tools.idea.gradle.util;

import com.android.SdkConstants;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.List;

public final class GradleBuilds {
  private static final Logger LOG = Logger.getInstance(GradleBuilds.class);

  @NonNls public static final String CLEAN_TASK_NAME = "clean";
  @NonNls private static final String DEFAULT_ASSEMBLE_TASK_NAME = "assemble";

  private GradleBuilds() {
  }

  public static void findAndAddBuildTask(@NotNull String moduleName,
                                         @NotNull BuildMode buildMode,
                                         @Nullable String gradleProjectPath,
                                         @Nullable JpsAndroidModuleProperties androidFacetProperties,
                                         @NotNull List<String> tasks,
                                         boolean buildTests) {
    if (gradleProjectPath == null) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String format = "Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.";
      String msg = String.format(format, moduleName);
      LOG.info(msg);
      return;
    }
    String assembleTaskName = null;
    if (androidFacetProperties != null) {
      switch (buildMode) {
        case SOURCE_GEN:
          assembleTaskName = androidFacetProperties.SOURCE_GEN_TASK_NAME;
          break;
        case COMPILE_JAVA:
          assembleTaskName = androidFacetProperties.COMPILE_JAVA_TASK_NAME;
          break;
        default:
          assembleTaskName = androidFacetProperties.ASSEMBLE_TASK_NAME;
      }
    }

    if (Strings.isNullOrEmpty(assembleTaskName) && !BuildMode.SOURCE_GEN.equals(buildMode)) {
      // We fall back to "assemble", for example when dealing with pure Java libraries.
      // When we are just generating code, we don't need to call "assemble" on pure Java libraries.
      assembleTaskName = DEFAULT_ASSEMBLE_TASK_NAME;
    }
    if (assembleTaskName != null) {
      tasks.add(createBuildTask(gradleProjectPath, assembleTaskName));
    }

    if (buildTests && androidFacetProperties != null) {
      String assembleTestTaskName = androidFacetProperties.ASSEMBLE_TEST_TASK_NAME;
      if (!Strings.isNullOrEmpty(assembleTestTaskName)) {
        tasks.add(createBuildTask(gradleProjectPath, assembleTestTaskName));
      }
    }
  }

  @NotNull
  private static String createBuildTask(@NotNull String gradleProjectPath, @NotNull String taskName) {
    return gradleProjectPath + SdkConstants.GRADLE_PATH_SEPARATOR + taskName;
  }
}
