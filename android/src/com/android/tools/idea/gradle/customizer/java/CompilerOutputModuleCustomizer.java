/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.customizer.AbstractCompileOutputModuleCustomizer;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.join;

/**
 * Sets the compiler output folder to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class CompilerOutputModuleCustomizer extends AbstractCompileOutputModuleCustomizer<JavaProject> {
  @NonNls private static final String CLASSES_FOLDER_NAME = "classes";

  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable JavaProject javaProject) {
    if (javaProject == null) {
      return;
    }
    File mainClassesFolder = null;
    File testClassesFolder = null;
    ExtIdeaCompilerOutput compilerOutput = javaProject.getCompilerOutput();
    if (compilerOutput == null) {
      File buildFolderPath = javaProject.getBuildFolderPath();
      if (buildFolderPath != null) {
        mainClassesFolder = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, "main"));
        testClassesFolder = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, "test"));
      }
    }
    else {
      mainClassesFolder = compilerOutput.getMainClassesDir();
      testClassesFolder = compilerOutput.getTestClassesDir();
    }

    if (mainClassesFolder != null) {
      // This folder is null for modules that are just folders containing other modules. This type of modules are later on removed by
      // PostProjectSyncTaskExecutor.
      final ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
      setOutputPaths(moduleModel, mainClassesFolder, testClassesFolder);
    }
  }
}
