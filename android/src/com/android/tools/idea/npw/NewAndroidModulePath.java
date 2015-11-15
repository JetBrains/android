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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.npw.ChooseTemplateStep.MetadataListItem;
import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_CREATE_ICONS;

/**
 * This class deals with the "main" flow of the new module wizard when
 * either Android application or Android library is added to the project.
 *
 * Deprecated. Use {@link NewModuleWizardDynamic} instead.
 */
@Deprecated
@LegacyWizardPathProvider.Migrated
public final class NewAndroidModulePath implements WizardPath {
  private static final Logger LOG = Logger.getInstance(NewAndroidModulePath.class);
  @NotNull private final NewModuleWizardState myWizardState;
  @Nullable private final Project myProject;
  private ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  private RasterAssetSetStep myAssetSetStep;
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
    myAssetSetStep = new RasterAssetSetStep(myWizardState, project, null, sidePanelIcon, builder, null);
    Disposer.register(disposable, myAssetSetStep);
    myChooseActivityStep = new ChooseTemplateStep(myWizardState.getActivityTemplateState(),
                                                  CATEGORY_ACTIVITIES,
                                                  project,
                                                  null,
                                                  sidePanelIcon,
                                                  builder,
                                                  null,
                                                  ContainerUtil.newHashSet("Android TV Activity")) {
      @Override
      public String getHelpId() {
        return "Android_Application_Template_Page";
      }
    };
    myActivityTemplateParameterStep =
      new TemplateParameterStep(myWizardState.getActivityTemplateState(), project, null, sidePanelIcon, builder) {
        @Override
        public String getHelpId() {
          return "Android_Activity_Settings_Page";
        }
      };
    myJavaModuleTemplateParameterStep = new TemplateParameterStep(myWizardState, project, null, sidePanelIcon, builder);
    myAssetSetStep.finalizeAssetType(AssetStudioAssetGenerator.AssetType.LAUNCHER);
  }

  @Override
  public void update() {
    boolean isAndroidTemplate = NewModuleWizardState.isAndroidTemplate(myWizardState.getTemplateMetadata());
    myJavaModuleTemplateParameterStep.setVisible(!isAndroidTemplate);
    myConfigureAndroidModuleStep.setVisible(isAndroidTemplate);
    if (isAndroidTemplate) {
      myConfigureAndroidModuleStep.updateStep();
    }
    myAssetSetStep.setVisible(isAndroidTemplate && myWizardState.getBoolean(ATTR_CREATE_ICONS));
    boolean createActivity = isAndroidTemplate && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY);
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

        // Let the catch block handle the NullPointerException
        @SuppressWarnings("ConstantConditions") File projectRoot = new File(myProject.getBasePath());
        File moduleRoot = new File(projectRoot, myWizardState.getString(FormFactorUtils.ATTR_MODULE_NAME));
        // TODO: handle return type of "mkdirs".
        projectRoot.mkdirs();
        myWizardState.updateParameters();
        Template template = myWizardState.myTemplate;
        // @formatter:off
        final RenderingContext context = RenderingContext.Builder.newContext(template, myProject)
          .withOutputRoot(projectRoot)
          .withModuleRoot(moduleRoot)
          .withParams(myWizardState.myParameters)
          .build();
        // @formatter:on
        template.render(context);

        if (NewModuleWizardState.isAndroidTemplate(template.getMetadata())) {
          if (myAssetSetStep.isStepVisible() && myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS)) {
            AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator(myWizardState);
            assetGenerator.outputImagesIntoDefaultVariant(moduleRoot);
          }
          if (myActivityTemplateParameterStep.isStepVisible() && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY)) {
            TemplateWizardState activityTemplateState = myWizardState.getActivityTemplateState();
            activityTemplateState.populateRelativePackage(null);
            Template activityTemplate = activityTemplateState.getTemplate();
            assert activityTemplate != null;
            // @formatter:off
            final RenderingContext activityContext = RenderingContext.Builder.newContext(activityTemplate, myProject)
              .withOutputRoot(moduleRoot)
              .withModuleRoot(moduleRoot)
              .withParams(activityTemplateState.myParameters)
              .build();
            // @formatter:on
            activityTemplate.render(activityContext);
          }
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
    return ImmutableSet.of(new ChooseAndroidAndJavaSdkStep(),
                           myJavaModuleTemplateParameterStep,
                           myConfigureAndroidModuleStep,
                           myAssetSetStep,
                           myChooseActivityStep,
                           myActivityTemplateParameterStep);
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
    File moduleTemplate =
      new File(TemplateManager.getTemplateRootFolder(), FileUtil.join(Template.CATEGORY_PROJECTS, WizardConstants.MODULE_TEMPLATE_NAME));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplateMetadata(moduleTemplate);

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

      @Nullable
      @Override
      public String getDescription() {
        return "Creates a new Android library module.";
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
