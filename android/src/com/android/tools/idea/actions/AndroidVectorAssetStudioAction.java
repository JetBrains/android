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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.PreciseRevision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.npw.VectorAssetStudioWizard;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;

/**
 * Action to invoke the Vector Asset Studio. This action is visible
 * anywhere within a module that has an Android facet.
 * It is extending the AndroidAssetStudioAction because the only difference is
 * showing a different wizard as VectorAssetStudioWizard.
 */
public class AndroidVectorAssetStudioAction extends AndroidAssetStudioAction {

  private static final String updateMessage =
    "<html><p>To support vector assets when minimal SDK version is less than 21,<br>" +
    "Android plugin for Gradle version must be 1.4 or above,<br>" +
    "such that Android Studio will convert vector assets into PNG images at build time.</p>" +
    "<p>See <a href=\"https://developer.android.com/tools/building/plugin-for-gradle.html" +
    "#projectBuildFile\">here</a> for how to update the version of Android plugin for Gradle." +
    "</p></html>";

  private static final FullRevision VECTOR_ASSET_GENERATION_REVISION = new FullRevision(1, 4, 0);
  private static final int VECTOR_DRAWABLE_API_LEVEL = 21;

  public AndroidVectorAssetStudioAction() {
    super("Vector Asset", "Open Vector Asset Studio to create an image asset", AndroidIcons.Android);
  }

  @Override
  protected void showWizardAndCreateAsset(Project project, Module module, VirtualFile targetFile) {
    // If min SDK is less than 21 and the Android plugin for Gradle version is less than 1.4,
    // then we want to show error message that vector assets won't be supported.
    AndroidGradleModel androidModel = AndroidGradleModel.get(module);
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      String version = androidModel.getAndroidProject().getModelVersion();
      FullRevision revision = PreciseRevision.parseRevision(version);

      if (revision.compareTo(VECTOR_ASSET_GENERATION_REVISION, FullRevision.PreviewComparison.IGNORE) < 0
          && (minSdkVersion == null || minSdkVersion.getApiLevel() < VECTOR_DRAWABLE_API_LEVEL)) {
        Messages.showErrorDialog(project, updateMessage, "Need Newer Android Plugin for Gradle");
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
