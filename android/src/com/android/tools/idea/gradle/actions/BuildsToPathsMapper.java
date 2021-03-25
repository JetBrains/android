/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p> Generates a map from module/build variant to the location of generated apk or bundle,
 * if it's a single apk, returns the apk file;
 * if there're multiple apks, returns the parent folder of apk files;
 * if it's app bundle, returns the bundle file.
 * <p>
 * {@link PostBuildModel} being built from the result of {@link OutputBuildAction} contains paths information of each of the build.
 */
public abstract class BuildsToPathsMapper {
  @NotNull
  public static BuildsToPathsMapper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BuildsToPathsMapper.class);
  }

  @NotNull
  public abstract Map<String, File> getBuildsToPaths(@Nullable Object model,
                                     @NotNull List<String> buildVariants,
                                     @NotNull Collection<Module> modules,
                                     boolean isAppBundle,
                                     @Nullable String signedApkOrBundlePath);
}