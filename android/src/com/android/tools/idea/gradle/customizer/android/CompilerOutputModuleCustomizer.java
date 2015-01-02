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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractCompileOutputModuleCustomizer;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.base.Strings;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Sets the compiler output folder to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class CompilerOutputModuleCustomizer extends AbstractCompileOutputModuleCustomizer<IdeaAndroidProject>
  implements BuildVariantModuleCustomizer<IdeaAndroidProject> {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject androidProject) {
    if (androidProject == null) {
      return;
    }
    String modelVersion = androidProject.getDelegate().getModelVersion();
    if (Strings.isNullOrEmpty(modelVersion)) {
      // We are dealing with old model that does not have 'class' folder.
      return;
    }
    Variant selectedVariant = androidProject.getSelectedVariant();
    File outputFile = selectedVariant.getMainArtifact().getClassesFolder();
    setOutputPaths(module, outputFile, null);
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return ProjectSystemId.IDE;
  }

  @Override
  public Class<IdeaAndroidProject> getSupportedModelType() {
    return IdeaAndroidProject.class;
  }
}
