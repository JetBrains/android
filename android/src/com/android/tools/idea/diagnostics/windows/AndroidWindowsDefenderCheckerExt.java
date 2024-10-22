/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.windows;

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.diagnostic.WindowsDefenderChecker;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Adds android specific paths to be excluded in {@link WindowsDefenderChecker}.
 * Project and system paths are added directly in {@link WindowsDefenderChecker},
 * Gradle specific paths in  {@link org.jetbrains.plugins.gradle.util.GradleWindowsDefenderCheckerExt}
 */
public class AndroidWindowsDefenderCheckerExt implements WindowsDefenderChecker.Extension {
  @Override
  public @NotNull Collection<Path> getPaths(@Nullable Project project, @Nullable Path projectPath) {
    List<Path> paths = new ArrayList<>();
    // Note: Do not include ".android" because
    // 1) the location cannot be customized by the user and
    // 2) the location is not write heavy (mostly read operations)
    //paths.add(Paths.get(homeDir, ".android"));
    File sdkPath = IdeSdks.getInstance().getAndroidSdkPath();
    if  (sdkPath != null) {
      paths.add(sdkPath.toPath());
    }
    return paths;
  }
}
