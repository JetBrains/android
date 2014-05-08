/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CREATE_ICONS;
import static com.android.tools.idea.wizard.ChooseTemplateStep.MetadataListItem;

/**
 * This class deals with the "main" flow of the new module wizard when
 * either Android application or Android library is added to the project.
 */
public final class NewAndroidModulePath implements WizardPath {
  private static final Logger LOG = Logger.getInstance(NewAndroidModulePath.class);
  @NotNull private final NewModuleWizardState myWizardState;
  @Nullable private final Project myProject;
  private ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  private AssetSetStep myAssetSetStep;
  private ChooseTemplateStep myChooseActivityStep;
  private TemplateParameterStep myActivityTemplateParameterStep;
  private TemplateParameterStep myJavaModuleTemplateParameterStep;

  public NewAndroidModulePath(@NotNull NewModuleWizardState wizardState,
                              @NotNull TemplateWizardStep.UpdateListener builder,
                              @Nullable Project project,
                              @Nullable Icon sidePanelIcon,
                              @NotNull Disposable disposable) {
    myWizardState = wizardState;
    myProject = project;
    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(wizardState, project, sidePanelIcon, builder);
    myAssetSetStep = new AssetSetStep(myWizardState, project, null, sidePanelIcon, builder, null);
    Disposer.register(disposable, myAssetSetStep);
    myChooseActivityStep =
      new ChooseTemplateStep(myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES, project, null, sidePanelIcon, builder, null);
    myActivityTemplateParameterStep = new TemplateParameterStep(myWizardState.getActivityTemplateState(), project, null, sidePanelIcon, builder);
    myJavaModuleTemplateParameterStep = new TemplateParameterStep(myWizardState, project, null, sidePanelIcon, builder);
    myAssetSetStep.finalizeAssetType(AssetStudioAssetGenerator.AssetType.LAUNCHER);
  }

  @Override
  public void update() {
    boolean isAndroidTemplate = NewModuleWizardState.isAndroidTemplate(myWizardState.getTemplateMetadata());
    myJavaModuleTemplateParameterStep.setVisible(!isAndroidTemplate);
    if (isAndroidTemplate) {
      myConfigureAndroidModuleStep.updateStep();
    }
    myAssetSetStep.setVisible(myWizardState.getBoolean(ATTR_CREATE_ICONS));
    boolean createActivity = myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY);
    myChooseActivityStep.setVisible(createActivity);
    myActivityTemplateParameterStep.setVisible(createActivity);
  }

  public void templateChanged() {
    myConfigureAndroidModuleStep.refreshUiFromParameters();
  }

  @Override
  public void createModule() {
    // For historical reasons, this class handles project creation for both Java and Android module templates
    if (myProject != null) {
      try {
        myWizardState.populateDirectoryParameters();
        File projectRoot = new File(myProject.getBasePath());
        File moduleRoot = new File(projectRoot, myWizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME));
        // TODO: handle return type of "mkdirs".
        projectRoot.mkdirs();
        myWizardState.updateParameters();
        myWizardState.myTemplate.render(projectRoot, moduleRoot, myWizardState.myParameters);
        if (myAssetSetStep.isStepVisible() && myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS)) {
          AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator(myWizardState);
          assetGenerator.outputImagesIntoDefaultVariant(moduleRoot);
        }
        if (myActivityTemplateParameterStep.isStepVisible() && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY)) {
          TemplateWizardState activityTemplateState = myWizardState.getActivityTemplateState();
          Template template = activityTemplateState.getTemplate();
          assert template != null;
          template.render(moduleRoot, moduleRoot, activityTemplateState.myParameters);
        }
      }
      catch (Exception e) {
        Messages.showErrorDialog(e.getMessage(), "New Module");
        LOG.error(e);
      }
    }
  }

  @Override
  public Collection<ModuleWizardStep> getSteps() {
    return ImmutableSet.of(new ChooseAndroidAndJavaSdkStep(), myJavaModuleTemplateParameterStep, myConfigureAndroidModuleStep,
                           myAssetSetStep, myChooseActivityStep, myActivityTemplateParameterStep);
  }

  @Override
  public boolean isStepVisible(ModuleWizardStep step) {
    if (!step.isStepVisible()) {
      return false;
    }
    else if (step instanceof ChooseAndroidAndJavaSdkStep) {
      return true;
    }
    else {
      return !NewModuleWizardState.isAndroidTemplate(myWizardState.getTemplateMetadata()) == (step == myJavaModuleTemplateParameterStep);
    }
  }

  @Override
  public Collection<MetadataListItem> getBuiltInTemplates() {
    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(Template.CATEGORY_PROJECTS, NewProjectWizardState.MODULE_TEMPLATE_NAME));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplate(moduleTemplate);

    assert metadata != null;

    MetadataListItem appListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return TemplateWizardModuleBuilder.APP_TEMPLATE_NAME;
      }
    };
    MetadataListItem libListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME;
      }
    };
    return ImmutableSet.of(appListItem, libListItem);
  }

  @Override
  public boolean supportsGlobalWizard() {
    return true;
  }

  @Override
  public Collection<String> getExcludedTemplates() {
    return ImmutableSet.of(TemplateWizardModuleBuilder.MODULE_NAME, TemplateWizardModuleBuilder.PROJECT_NAME);
  }
}
