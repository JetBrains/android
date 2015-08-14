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
package com.android.tools.idea.gradle;

import com.android.SdkConstants;
import com.google.common.collect.Lists;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Contains Gradle related state necessary for building an IDEA module using Gradle.
 */
public class GradleModel implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myModuleName;
  @NotNull private final List<String> myTaskNames;
  @NotNull private final String myGradlePath;

  @Nullable private final File myBuildFile;
  @Nullable private final String myGradleVersion;


  /**
   * Creates a new {@link GradleModel}.
   * @param moduleName    the name of the IDEA module.
   * @param gradleProject the Gradle project.
   * @param buildFile     the build.gradle file.
   * @param gradleVersion the version of Gradle used to sync the project.
   */
  public static GradleModel create(@NotNull String moduleName,
                                   @NotNull GradleProject gradleProject,
                                   @Nullable File buildFile,
                                   @Nullable String gradleVersion) {
    List<String> taskNames = Lists.newArrayList();
    DomainObjectSet<? extends GradleTask> tasks = gradleProject.getTasks();
    if (!tasks.isEmpty()) {
      for (GradleTask task : tasks) {
        String name = task.getName();
        if (isNotEmpty(name)) {
          taskNames.add(task.getProject().getPath() + SdkConstants.GRADLE_PATH_SEPARATOR + task.getName());
        }
      }
    }

    return new GradleModel(moduleName, taskNames, gradleProject.getPath(), buildFile, gradleVersion);
  }

  public GradleModel(@NotNull String moduleName,
                     @NotNull List<String> taskNames,
                     @NotNull String gradlePath,
                     @Nullable File buildFile,
                     @Nullable String gradleVersion) {
    myModuleName = moduleName;
    myTaskNames = taskNames;
    myGradlePath = gradlePath;
    myBuildFile = buildFile;
    myGradleVersion = gradleVersion;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * @return the path of the Gradle project.
   */
  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @NotNull
  public List<String> getTaskNames() {
    return myTaskNames;
  }

  @Nullable
  public VirtualFile getBuildFile() {
    return myBuildFile != null ? VfsUtil.findFileByIoFile(myBuildFile, true) : null;
  }

  @Nullable
  public String getGradleVersion() {
    return myGradleVersion;
  }
}
