/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.npw.VectorAssetStudioWizard;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action to invoke the Vector Asset Studio. This will allow the user to generate icons using SVGs.
 */
public class NewVectorAssetAction extends AndroidAssetStudioAction {

  private static final String ERROR_TITLE = "Newer Android Plugin for Gradle Required";
  private static final String ERROR_MESSAGE =
    "<html><p>To support vector assets when your minimal SDK version is less than 21,<br>" +
    "the Android plugin for Gradle version must be 1.4 or above.<br>" +
    "This will allow Android Studio to convert vector assets into PNG images at build time.</p>" +
    "<p>See <a href=\"https://developer.android.com/tools/building/plugin-for-gradle.html" +
    "#projectBuildFile\">here</a> for how to update the version of Android plugin for Gradle." +
    "</p></html>";

  private static final GradleVersion VECTOR_ASSET_GENERATION_REVISION = new GradleVersion(1, 4, 0);
  private static final int VECTOR_DRAWABLE_API_LEVEL = 21;

  public NewVectorAssetAction() {
    super("Vector Asset", "Open Vector Asset Studio to create an image asset");
  }

  @Override
  protected void showWizardAndCreateAsset(@NotNull AndroidFacet facet, @Nullable VirtualFile targetFile) {
    // If min SDK is less than 21 and the Android plugin for Gradle version is less than 1.4,
    // then we want to show error message that vector assets won't be supported.
    Module module = facet.getModule();
    Project project = module.getProject();
    AndroidGradleModel androidModel = AndroidGradleModel.get(module);
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      String version = androidModel.getAndroidProject().getModelVersion();
      GradleVersion revision = GradleVersion.parse(version);

      if (revision.compareIgnoringQualifiers(VECTOR_ASSET_GENERATION_REVISION) < 0
          && (minSdkVersion == null || minSdkVersion.getApiLevel() < VECTOR_DRAWABLE_API_LEVEL)) {
        Messages.showErrorDialog(project, ERROR_MESSAGE, ERROR_TITLE);
        return;
      }
    }
    VectorAssetStudioWizard dialog = new VectorAssetStudioWizard(project, module, targetFile);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.createAssets();
  }
}
