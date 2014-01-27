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

import com.android.tools.idea.gradle.facet.JavaModel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

/**
 * Contains Gradle related state necessary for building an IDEA module using Gradle.
 */
public class IdeaGradleProject implements Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final VirtualFile myBuildFile;
  @NotNull private final String myGradleProjectPath;

  private JavaModel myJavaModel;

  /**
   * Creates a new {@link IdeaGradleProject}.
   *
   * @param moduleName        the name of the IDEA module.
   * @param buildFile         the build.gradle file.
   * @param gradleProjectPath Gradle project path.
   */
  public IdeaGradleProject(@NotNull String moduleName, @NotNull File buildFile, @NotNull String gradleProjectPath) {
    myModuleName = moduleName;
    VirtualFile found = VfsUtil.findFileByIoFile(buildFile, true);
    // the build.gradle file can never be null.
    assert found != null;
    myBuildFile = found;
    myGradleProjectPath = gradleProjectPath;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * @return the path of the Gradle project.
   */
  @NotNull
  public String getGradleProjectPath() {
    return myGradleProjectPath;
  }

  @NotNull
  public VirtualFile getBuildFile() {
    return myBuildFile;
  }

  @Nullable
  public JavaModel getJavaModel() {
    return myJavaModel;
  }

  public void setJavaModel(@NotNull JavaModel javaModel) {
    myJavaModel = javaModel;
  }
}
