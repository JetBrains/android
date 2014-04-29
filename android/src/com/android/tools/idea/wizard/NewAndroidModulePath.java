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

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CREATE_ICONS;

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
  private boolean myIsActivePath;

  public NewAndroidModulePath(@NotNull NewModuleWizardState wizardState,
                              @NotNull TemplateWizardModuleBuilder builder,
                              @Nullable Project project,
                              @Nullable Icon sidePanelIcon,
                              @NotNull Disposable disposable) {
    myWizardState = wizardState;
    myProject = project;
    NewModuleWizardState state = builder.myWizardState;
    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(state, project, sidePanelIcon, builder);
    myAssetSetStep = new AssetSetStep(state, project, null, sidePanelIcon, builder, null);
    Disposer.register(disposable, myAssetSetStep);
    myChooseActivityStep =
      new ChooseTemplateStep(state.getActivityTemplateState(), CATEGORY_ACTIVITIES, project, null, sidePanelIcon, builder, null);
    myActivityTemplateParameterStep = new TemplateParameterStep(state.getActivityTemplateState(), project, null, sidePanelIcon, builder);
    myAssetSetStep.finalizeAssetType(AssetStudioAssetGenerator.AssetType.LAUNCHER);
  }

  @Override
  public void update() {
    myIsActivePath = myWizardState.myIsAndroidModule;
    myConfigureAndroidModuleStep.setVisible(myIsActivePath);
    if (myIsActivePath) {
      myConfigureAndroidModuleStep.updateStep();
    }
    myAssetSetStep.setVisible(myIsActivePath && myWizardState.getBoolean(ATTR_CREATE_ICONS));

    boolean createActivity = myIsActivePath && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY);
    myChooseActivityStep.setVisible(createActivity);
    myActivityTemplateParameterStep.setVisible(createActivity);
  }

  public void templateChanged() {
    myConfigureAndroidModuleStep.refreshUiFromParameters();
  }

  @Override
  public void createModule() {
    if (!myWizardState.myIsModuleImport && myProject != null) {
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
    return ImmutableSet
      .<ModuleWizardStep>of(myConfigureAndroidModuleStep, myAssetSetStep, myChooseActivityStep, myActivityTemplateParameterStep);
  }

  @Override
  public boolean isStepVisible(ModuleWizardStep step) {
    return myIsActivePath && step.isStepVisible();
  }
}
