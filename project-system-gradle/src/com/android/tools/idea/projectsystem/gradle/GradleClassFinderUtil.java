/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class GradleClassFinderUtil {
  private GradleClassFinderUtil() {};

  @NotNull
  private static Stream<File> getCompilerOutputRoots(@NotNull GradleAndroidModel model, boolean includeAndroidTests) {
    List<IdeAndroidArtifact> artifacts = new ArrayList<>();
    artifacts.add(model.getMainArtifact());
    if (includeAndroidTests) {
      artifacts.add(model.getArtifactForAndroidTest());
    }
    return artifacts.stream().flatMap((artifactInfo) -> artifactInfo.getClassesFolder().stream());
  }

  /**
   * Returns all the compiler output roots for the given {@link Module}. If {@code includeAndroidTests} is true, it will include
   * also the output for tests.
   */
  @NotNull
  public static Stream<File> getModuleCompileOutputs(@NotNull Module module, boolean includeAndroidTests) {
    GradleAndroidModel androidModel = GradleAndroidModel.get(module);
    if (androidModel != null) {
      return getCompilerOutputRoots(androidModel, includeAndroidTests);
    }

    // The module is not an Android module. Check for regular Java outputs.
    Module[] modules = {module};
    return Stream.of(CompilerPaths.getOutputPaths(modules))
      .map(File::new);
  }
}
