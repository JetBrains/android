/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.common.CompilerSettingsSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.join;

public class CompilerOutputModuleSetupStep extends JavaModuleSetupStep {
  @NonNls private static final String CLASSES_FOLDER_NAME = "classes";

  @NotNull private final CompilerSettingsSetup myCompilerSettingsSetup;

  public CompilerOutputModuleSetupStep() {
    this(new CompilerSettingsSetup());
  }

  @VisibleForTesting
  CompilerOutputModuleSetupStep(@NotNull CompilerSettingsSetup compilerSettingsSetup) {
    myCompilerSettingsSetup = compilerSettingsSetup;
  }

  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull JavaModuleModel javaModuleModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    File mainClassesFolderPath = null;
    File testClassesFolderPath = null;
    ExtIdeaCompilerOutput compilerOutput = javaModuleModel.getCompilerOutput();
    if (compilerOutput != null) {
      mainClassesFolderPath = compilerOutput.getMainClassesDir();
      testClassesFolderPath = compilerOutput.getTestClassesDir();
    }
    if (javaModuleModel.isBuildable()) {
      // See: http://b/65513580
      File buildFolderPath = javaModuleModel.getBuildFolderPath();
      if (mainClassesFolderPath == null) {
        mainClassesFolderPath = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, "main"));
      }
      if (testClassesFolderPath == null) {
        testClassesFolderPath = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, "test"));
      }
    }

    if (mainClassesFolderPath != null) {
      // This folder is null for modules that are just folders containing other modules. This type of modules are later on removed by
      // PostProjectSyncTaskExecutor.
      ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
      myCompilerSettingsSetup.setOutputPaths(moduleModel, mainClassesFolderPath, testClassesFolderPath);
    }
  }

  @Override
  public boolean invokeOnSkippedSync() {
    return true;
  }
}
