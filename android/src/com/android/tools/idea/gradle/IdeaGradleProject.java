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
import com.android.tools.idea.gradle.facet.JavaModel;
import com.google.common.base.Strings;
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

/**
 * Contains Gradle related state necessary for building an IDEA module using Gradle.
 */
public class IdeaGradleProject implements Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final List<String> myTaskNames;
  @NotNull private final String myGradlePath;
  @Nullable private final File myBuildFile;

  private JavaModel myJavaModel;

  /**
   * Creates a new {@link IdeaGradleProject}.
   *
   * @param moduleName    the name of the IDEA module.
   * @param gradleProject the Gradle project.
   * @param buildFile     the build.gradle file.
   */
  public static IdeaGradleProject newIdeaGradleProject(@NotNull String moduleName, @NotNull GradleProject gradleProject, @Nullable File buildFile) {
    List<String> taskNames = Lists.newArrayList();
    DomainObjectSet<? extends GradleTask> tasks = gradleProject.getTasks();
    if (!tasks.isEmpty()) {
      for (GradleTask task : tasks) {
        String name = task.getName();
        if (!Strings.isNullOrEmpty(name)) {
          taskNames.add(task.getProject().getPath() + SdkConstants.GRADLE_PATH_SEPARATOR + task.getName());
        }
      }
    }

    return new IdeaGradleProject(moduleName, taskNames, gradleProject.getPath(), buildFile);
  }

  public IdeaGradleProject(@NotNull String moduleName, @NotNull List<String> taskNames, @NotNull String gradlePath, @Nullable File buildFile) {
    myModuleName = moduleName;
    myTaskNames = taskNames;
    myGradlePath = gradlePath;
    myBuildFile = buildFile;
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
  public File getIoBuildFile() {
    return myBuildFile;
  }

  @Nullable
  public VirtualFile getBuildFile() {
    return myBuildFile != null ? VfsUtil.findFileByIoFile(myBuildFile, true) : null;
  }

  @Nullable
  public JavaModel getJavaModel() {
    return myJavaModel;
  }

  public void setJavaModel(@NotNull JavaModel javaModel) {
    myJavaModel = javaModel;
  }
}
