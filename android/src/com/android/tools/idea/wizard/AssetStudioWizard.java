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
package com.android.tools.idea.wizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

import static com.android.tools.idea.wizard.TemplateWizardStep.NONE;

/**
 * Wizard that allows the user to create various density-scaled assets.
 */
public class AssetStudioWizard extends TemplateWizard implements TemplateWizardStep.UpdateListener {
  protected Module myModule;
  protected AssetStudioWizardState myState = new AssetStudioWizardState();
  protected ChooseOutputLocationStep myOutputStep;

  public AssetStudioWizard(@Nullable Project project, @Nullable Module module) {
    super("Asset Studio", project);
    myModule = module;
    getWindow().setMinimumSize(new Dimension(800, 640));
    init();
  }

  @Override
  protected void init() {
    AssetSetStep iconStep = new AssetSetStep(myState, myProject, null, this);
    myOutputStep = new ChooseOutputLocationStep(myState, myProject, null, NONE, myModule);
    addStep(iconStep);
    addStep(myOutputStep);

    super.init();
  }

  @Override
  public void update() {
    super.update();
    if (myOutputStep != null) {
      myOutputStep.updateStep();
    }
  }

  public void createAssets() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        File targetResDir = (File)myState.get(ChooseOutputLocationStep.ATTR_OUTPUT_FOLDER);
        if (targetResDir == null) {
          return;
        }

        File targetVariantDir = targetResDir.getParentFile();

        myState.outputImagesIntoVariantRoot(targetVariantDir);

        VirtualFile resDir = LocalFileSystem.getInstance().findFileByIoFile(targetResDir);
        if (resDir != null) {
          // Refresh the res directory so that the new files show up in the IDE.
          resDir.refresh(true, true);
        } else {
          // If we can't find the res directory, refresh the project.
          myProject.getBaseDir().refresh(true, true);
        }
      }
    });
  }
}
