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

import com.android.tools.idea.gradle.customizer.AbstractCompileOutputModuleCustomizer;
import com.android.tools.idea.gradle.facet.JavaModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

import java.io.File;

/**
 * Sets the compiler output folder to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class CompilerOutputModuleCustomizer extends AbstractCompileOutputModuleCustomizer<JavaModel> {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable JavaModel javaModel) {
    if (javaModel == null) {
      return;
    }
    ExtIdeaCompilerOutput compilerOutput = javaModel.getCompilerOutput();
    File mainClassesFolder = compilerOutput.getMainClassesDir();
    if (mainClassesFolder != null) {
      // This folder is null for modules that are just folders containing other modules. This type of modules are later on removed by
      // PostProjectSyncTaskExecutor.
      setOutputPaths(module, mainClassesFolder, compilerOutput.getTestClassesDir());
    }
  }
}
