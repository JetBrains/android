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

import com.android.tools.idea.npw.VectorAssetStudioWizard;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;

/**
 * Action to invoke the Vector Asset Studio. This action is visible
 * anywhere within a module that has an Android facet.
 * It is extending the AndroidAssetStudioAction because the only difference is
 * showing a different wizard as VectorAssetStudioWizard.
 */
public class AndroidVectorAssetStudioAction extends AndroidAssetStudioAction {

  public AndroidVectorAssetStudioAction() {
    super("Vector Asset", "Open Vector Asset Studio to create an image asset", AndroidIcons.Android);
  }

  @Override
  protected void showWizardAndCreateAsset(Project project, Module module, VirtualFile targetFile) {
    VectorAssetStudioWizard dialog = new VectorAssetStudioWizard(project, module, targetFile);
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.createAssets();
  }
}
