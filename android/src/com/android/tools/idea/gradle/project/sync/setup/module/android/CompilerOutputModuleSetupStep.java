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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.common.CompilerSettingsSetup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CompilerOutputModuleSetupStep extends AndroidModuleSetupStep {
  private final CompilerSettingsSetup myCompilerSettingsSetup = new CompilerSettingsSetup();

  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull AndroidModuleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    GradleVersion modelVersion = androidModel.getModelVersion();
    if (modelVersion == null) {
      // We are dealing with old model that does not have the 'class' folder.
      return;
    }

    Variant selectedVariant = androidModel.getSelectedVariant();
    File mainClassesFolder = selectedVariant.getMainArtifact().getClassesFolder();

    JavaArtifact testArtifact = androidModel.getSelectedVariant().getUnitTestArtifact();
    File testClassesFolder = testArtifact == null ? null : testArtifact.getClassesFolder();

    ModifiableRootModel rootModel = ideModelsProvider.getModifiableRootModel(module);
    myCompilerSettingsSetup.setOutputPaths(rootModel, mainClassesFolder, testClassesFolder);
  }

  @Override
  public boolean invokeOnBuildVariantChange() {
    return true;
  }

  @Override
  public boolean invokeOnSkippedSync() {
    // See bug http://b.android.com/233410
    return true;
  }
}
