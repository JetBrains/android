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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.ChooseOutputResDirStep;
import com.android.tools.idea.npw.RasterAssetSetStep;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.android.tools.idea.wizard.template.TemplateWizardStep.NONE;

/**
 * Wizard that allows the user to create various density-scaled assets.
 */
public class AssetStudioWizard extends TemplateWizard implements TemplateWizardStep.UpdateListener {
  protected Module myModule;
  protected VirtualFile myTargetFile;
  protected TemplateWizardState myWizardState = new TemplateWizardState();
  protected ChooseOutputResDirStep myOutputStep;
  protected RasterAssetSetStep myIconStep;

  public AssetStudioWizard(@Nullable Project project, @Nullable Module module, @Nullable VirtualFile targetFile) {
    super("Asset Studio", project);
    myModule = module;
    myTargetFile = targetFile;
    getWindow().setMinimumSize(JBUI.size(800, 640));
    init();
  }

  @Override
  protected void init() {
    myIconStep = new RasterAssetSetStep(myWizardState, myProject, myModule, null, this, myTargetFile);
    Disposer.register(getDisposable(), myIconStep);
    myOutputStep = new ChooseOutputResDirStep(myWizardState, myProject, null, NONE, myModule, myTargetFile);
    addStep(myIconStep);
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
        myIconStep.createAssets(null);
      }
    });
  }
}
