/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.project.ModuleBasedClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleClassFileFinder extends ModuleBasedClassFileFinder {
  @NotNull private static final String CLASSES_FOLDER_NAME = "classes";
  @NotNull private static final String RESOURCES_FOLDER_NAME = "resources";
  @NotNull private static final String MAIN_FOLDER_NAME = "main";
  @NotNull private static final String TEST_FOLDER_NAME = "test";
  @NotNull private static final String KOTLIN_FOLDER_NAME = "kotlin";

  private final boolean includeAndroidTests;

  public GradleClassFileFinder(@NotNull Module module) {
    this(module, false);
  }

  public GradleClassFileFinder(@NotNull Module module, boolean includeAndroidTests) {
    super(module);
    this.includeAndroidTests = includeAndroidTests;
  }

  @Override
  @Nullable
  protected VirtualFile findClassFileInModule(@NotNull Module module, @NotNull String className) {
    for (VirtualFile outputDir : getModuleCompileOutputs(module)) {
      VirtualFile file = ClassFileFinderUtil.findClassFileInOutputRoot(outputDir, className);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  @NotNull
  private Collection<VirtualFile> getCompilerOutputRoots(@NotNull GradleAndroidModel model) {
    List<IdeAndroidArtifact> artifacts = new ArrayList<>();
    artifacts.add(model.getMainArtifact());
    if (includeAndroidTests) {
      artifacts.add(model.getArtifactForAndroidTest());
    }
    ImmutableList.Builder<VirtualFile> compilerOutputs = new ImmutableList.Builder<>();

    for (IdeAndroidArtifact artifactInfo : artifacts) {
      File classesFolder = artifactInfo.getClassesFolder();

      //noinspection ConstantConditions
      if (classesFolder != null) {
        if (classesFolder.exists()) {
          VirtualFile file = VfsUtil.findFileByIoFile(classesFolder, true);
          if (file != null) {
            compilerOutputs.add(file);
          }
        }
      }

      for (File additionalFolder : artifactInfo.getAdditionalClassesFolders()) {
        VirtualFile file = VfsUtil.findFileByIoFile(additionalFolder, true);
        if (file != null) {
          compilerOutputs.add(file);
        }
      }
    }

    return compilerOutputs.build();
  }

  @NotNull
  private Collection<VirtualFile> getModuleCompileOutputs(@NotNull Module module) {
    GradleAndroidModel androidModel = GradleAndroidModel.get(module);
    if (androidModel != null) {
      return getCompilerOutputRoots(androidModel);
    }

    // The module is not an Android module. Check for regular Java outputs.
    Module[] modules = {module};
    return Stream.of(CompilerPaths.getOutputPaths(modules))
      .map(path -> VfsUtil.findFileByIoFile(new File(path), true))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
