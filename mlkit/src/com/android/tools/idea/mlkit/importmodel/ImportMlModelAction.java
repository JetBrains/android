/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Action to import machine learning model to Android project.
 */
public class ImportMlModelAction extends AnAction {
  @VisibleForTesting static final String MIN_AGP_VERSION = "4.1.0-alpha04";
  @VisibleForTesting static final String TITLE = "TensorFlow Lite Model";
  @VisibleForTesting static final int MIN_SDK_VERSION = 19;

  public ImportMlModelAction() {
    super(TITLE, null, StudioIcons.Shell.Filetree.ANDROID_FILE);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    List<NamedModuleTemplate> moduleTemplates = getModuleTemplates(e);
    if (!moduleTemplates.isEmpty() && module != null && e.getProject() != null) {
      String title = "Import TensorFlow Lite model";
      ModelWizard wizard = new ModelWizard.Builder()
        .addStep(new ChooseMlModelStep(new MlWizardModel(module), moduleTemplates, e.getProject(), title))
        .build();
      new StudioWizardDialogBuilder(wizard, title).build().show();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Module module = PlatformCoreDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      presentation.setEnabled(false);
      return;
    }

    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(project);
    if (androidPluginInfo == null) {
      presentation.setEnabled(false);
      return;
    }

    AgpVersion agpVersion = androidPluginInfo.getPluginVersion();
    if (agpVersion == null || agpVersion.compareTo(MIN_AGP_VERSION) < 0) {
      presentation.setEnabled(false);
      presentation.setText(AndroidBundle.message("android.wizard.action.requires.new.agp", TITLE, MIN_AGP_VERSION));
      return;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      presentation.setEnabled(false);
      return;
    }

    if (AndroidModuleInfo.getInstance(androidFacet).getMinSdkVersion().getFeatureLevel() < MIN_SDK_VERSION) {
      presentation.setEnabled(false);
      presentation.setText(AndroidBundle.message("android.wizard.action.requires.minsdk", TITLE, MIN_SDK_VERSION));
      return;
    }

    if (getModuleTemplates(e).isEmpty()) {
      presentation.setEnabled(false);
    }

    presentation.setEnabledAndVisible(true);
  }

  /**
   * Gets a list of available {@link NamedModuleTemplate} from {@link AnActionEvent}.
   */
  @NotNull
  private static List<NamedModuleTemplate> getModuleTemplates(@NotNull AnActionEvent e) {
    if (e.getProject() == null) {
      return Collections.emptyList();
    }
    Module module = PlatformCoreDataKeys.MODULE.getData(e.getDataContext());
    VirtualFile virtualFile = e.getProject().getProjectFile();
    if (module == null || virtualFile == null) {
      return Collections.emptyList();
    }
    return ProjectSystemUtil.getModuleSystem(module).getModuleTemplates(virtualFile);
  }
}
