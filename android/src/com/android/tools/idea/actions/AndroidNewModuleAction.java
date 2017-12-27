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

package com.android.tools.idea.actions;

import com.android.tools.idea.npw.module.ChooseModuleTypeStep;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

import static org.jetbrains.android.util.AndroidBundle.message;

public class AndroidNewModuleAction extends AnAction implements DumbAware {
  public AndroidNewModuleAction() {
    super(message("android.wizard.module.new.module.menu"), message("android.wizard.module.new.module.menu.description"), null);
  }

  public AndroidNewModuleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
        SdkQuickfixUtils.showSdkMissingDialog();
        return;
      }

      ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
      for (ModuleDescriptionProvider provider : ModuleDescriptionProvider.EP_NAME.getExtensions()) {
        moduleDescriptions.addAll(provider.getDescriptions());
      }

      ChooseModuleTypeStep chooseModuleTypeStep = new ChooseModuleTypeStep(new NewModuleModel(project), moduleDescriptions);
      ModelWizard wizard = new ModelWizard.Builder().addStep(chooseModuleTypeStep).build();

      new StudioWizardDialogBuilder(wizard, message("android.wizard.module.new.module.title")).setUseNewUx(true).build().show();
    }
  }
}
