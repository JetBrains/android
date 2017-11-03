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

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * An action to launch a wizard to create a component from a template.
 */
public class NewAndroidComponentAction extends AnAction {
  // These categories will be using a new wizard
  public static Set<String> NEW_WIZARD_CATEGORIES = ImmutableSet.of("Activity", "Google");

  private final String myTemplateCategory;
  private final String myTemplateName;
  private final int myMinSdkVersion;

  public NewAndroidComponentAction(@NotNull String templateCategory, @NotNull String templateName, @Nullable TemplateMetadata metadata) {
    super(templateName, AndroidBundle.message("android.wizard.action.new.component", templateName), null);
    myTemplateCategory = templateCategory;
    myTemplateName = templateName;
    getTemplatePresentation().setIcon(isActivityTemplate() ? AndroidIcons.Activity : AndroidIcons.AndroidFile);
    myMinSdkVersion = metadata == null ? 0 : metadata.getMinSdk();
  }

  private boolean isActivityTemplate() {
    return NEW_WIZARD_CATEGORIES.contains(myTemplateCategory);
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return;
    }
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
    if (moduleInfo == null) {
      return;
    }

    Presentation presentation = e.getPresentation();
    int moduleMinSdkVersion = moduleInfo.getMinSdkVersion().getApiLevel();
    if (myMinSdkVersion > moduleMinSdkVersion) {
      presentation.setText(AndroidBundle.message("android.wizard.action.requires.minsdk", myTemplateName, myMinSdkVersion));
      presentation.setEnabled(false);
    }
    else {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      boolean isProjectReady = facet != null && facet.getAndroidModel() != null;
      presentation.setEnabled(isProjectReady);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      return;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || facet.getAndroidModel() == null) {
      return;
    }

    VirtualFile targetDirectory = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    // If the user selected a simulated folder entry (eg "Manifests"), there will be no target directory
    if (targetDirectory != null && !targetDirectory.isDirectory()) {
      targetDirectory = targetDirectory.getParent();
      assert targetDirectory != null;
    }

    File file = TemplateManager.getInstance().getTemplateFile(myTemplateCategory, myTemplateName);
    assert file != null;

    String activityDescription = e.getPresentation().getText(); // e.g. "Empty Activity", "Tabbed Activity"
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, targetDirectory);
    assert !sourceSets.isEmpty();

    String initialPackageSuggestion = targetDirectory == null
                                      ? AndroidPackageUtils.getPackageForApplication(facet)
                                      : AndroidPackageUtils.getPackageForPath(facet, sourceSets, targetDirectory);
    Project project = module.getProject();

    RenderTemplateModel templateModel = new RenderTemplateModel(
      project, new TemplateHandle(file), initialPackageSuggestion, sourceSets.get(0), "New " + activityDescription);

    boolean isActivity = isActivityTemplate();
    String dialogTitle = AndroidBundle.message(isActivity ? "android.wizard.new.activity.title" : "android.wizard.new.component.title");
    String stepTitle = AndroidBundle.message(isActivity ? "android.wizard.config.activity.title" : "android.wizard.config.component.title");

    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new ConfigureTemplateParametersStep(templateModel, stepTitle, sourceSets, facet));
    ModelWizardDialog dialog = new StudioWizardDialogBuilder(wizardBuilder.build(), dialogTitle).setProject(project).build();
    dialog.show();

    /*
    // TODO: Implement the getCreatedElements call for the wizard
    final PsiElement[] createdElements = dialog.getCreatedElements();

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
    */
  }
}
