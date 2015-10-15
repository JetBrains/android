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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.AbstractCompileOutputModuleCustomizer;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * Sets the compiler output folder to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class CompilerOutputModuleCustomizer extends AbstractCompileOutputModuleCustomizer<AndroidGradleModel>
  implements BuildVariantModuleCustomizer<AndroidGradleModel> {
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable AndroidGradleModel androidProject) {
    if (androidProject == null) {
      return;
    }
    String modelVersion = androidProject.getAndroidProject().getModelVersion();
    if (isEmpty(modelVersion)) {
      // We are dealing with old model that does not have 'class' folder.
      return;
    }
    Variant selectedVariant = androidProject.getSelectedVariant();
    File mainClassesFolder = selectedVariant.getMainArtifact().getClassesFolder();
    BaseArtifact testArtifact = androidProject.findSelectedTestArtifact(selectedVariant);
    File testClassesFolder = testArtifact == null ? null : testArtifact.getClassesFolder();

    final ModifiableRootModel ideaModuleModel = modelsProvider.getModifiableRootModel(module);
    setOutputPaths(ideaModuleModel, mainClassesFolder, testClassesFolder);
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return ProjectSystemId.IDE;
  }

  @Override
  @NotNull
  public Class<AndroidGradleModel> getSupportedModelType() {
    return AndroidGradleModel.class;
  }
}
