/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import com.android.java.model.ArtifactModel;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.project.model.JavaModuleModel.isBuildable;

/**
 * Factory class to create JavaModuleModel instance from ArtifactModel returned by Java Library plugin.
 */
public class ArtifactModuleModelFactory {
  @NotNull
  public JavaModuleModel create(@NotNull GradleProject gradleProject, @NotNull ArtifactModel jarAarProject) {
    return new JavaModuleModel(jarAarProject.getName(), getContentRoots(gradleProject), Collections.emptyList(), Collections.emptyList(),
                               jarAarProject.getArtifactsByConfiguration(), null, gradleProject.getBuildDirectory(), null,
                               isBuildable(gradleProject), false);
  }

  @NotNull
  private static Collection<JavaModuleContentRoot> getContentRoots(@NotNull GradleProject gradleProject) {
    // Exclude directory came from idea plugin, Java Library Plugin doesn't return this information.
    // Manually add build directory.
    Collection<File> excludeDirPaths = new ArrayList<>();
    excludeDirPaths.add(gradleProject.getBuildDirectory());
    return Collections.singleton(
      new JavaModuleContentRoot(gradleProject.getProjectDirectory(), Collections.emptyList(), Collections.emptyList(),
                                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                excludeDirPaths));
  }
}
