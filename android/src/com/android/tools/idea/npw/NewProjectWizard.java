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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_JAVA_VERSION;
import static icons.AndroidIcons.Wizards.NewProjectSidePanel;

/**
 * NewProjectWizard runs the wizard for creating entirely new Android projects. It takes the user
 * through steps to configure the project, setting its location and build parameters, and allows
 * the user to choose an activity to populate it. The wizard is template-driven, using templates
 * that live in the ADK.
 * Deprecated by {@link NewProjectWizardDynamic}
 */
@Deprecated
public class NewProjectWizard extends TemplateWizard implements TemplateWizardStep.UpdateListener {
  private static final Logger LOG = Logger.getInstance("#" + NewProjectWizard.class.getName());
  private static final String ERROR_MSG_TITLE = "New Project Wizard";
  private static final String UNABLE_TO_CREATE_DIR_FORMAT = "Unable to create directory '%1$s'.";

  @VisibleForTesting NewProjectWizardState myWizardState;

  @VisibleForTesting AssetStudioAssetGenerator myAssetGenerator;

  @VisibleForTesting RasterAssetSetStep myAssetSetStep;

  @VisibleForTesting TemplateGalleryStep myChooseActivityStep;

  @VisibleForTesting TemplateParameterStep myActivityParameterStep;

  @VisibleForTesting ConfigureAndroidModuleStep myConfigureAndroidModuleStep;

  @VisibleForTesting boolean myInitializationComplete = false;

  public NewProjectWizard() {
    super("New Project", null);
    Window window = getWindow();
    // Allow creation in headless mode for tests
    if (window != null) {
      getWindow().setMinimumSize(JBUI.size(1000, 640));
    }
    else {
      // We should always have a window unless we're in test mode
      assert ApplicationManager.getApplication().isUnitTestMode();
    }
    init();
  }

  @Override
  protected void init() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg =
        "<html>Your Android SDK is missing, out of date, or is missing templates. Please ensure you are using SDK version " +
        VersionCheck.MIN_TOOLS_REV + " or later.<br>" +
        "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      super.init();
      Messages.showErrorDialog(msg, title);
      throw new IllegalStateException(msg);
    }
    myWizardState = new NewProjectWizardState();
    Template.convertApisToInt(myWizardState.getParameters());
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GRADLE_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GRADLE_PLUGIN_RECOMMENDED_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_PER_MODULE_REPOS, false);

    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(myWizardState, myProject, NewProjectSidePanel, this);
    myConfigureAndroidModuleStep.updateStep();
    myAssetSetStep = new RasterAssetSetStep(myWizardState, myProject, null, NewProjectSidePanel, this, null);
    Disposer.register(getDisposable(), myAssetSetStep);
    myAssetGenerator = new AssetStudioAssetGenerator(myWizardState);
    myAssetSetStep.finalizeAssetType(AssetStudioAssetGenerator.AssetType.LAUNCHER);
    myChooseActivityStep =
      new TemplateGalleryStep(myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES, myProject, null, NewProjectSidePanel, this, null);
    myActivityParameterStep = new TemplateParameterStep(myWizardState.getActivityTemplateState(), myProject, null, NewProjectSidePanel, this);

    mySteps.add(myConfigureAndroidModuleStep);
    mySteps.add(myAssetSetStep);
    mySteps.add(myChooseActivityStep);
    mySteps.add(myActivityParameterStep);

    myInitializationComplete = true;
    super.init();
  }

  @Override
  public void update() {
    if (!myInitializationComplete) {
      return;
    }
    myAssetSetStep.setVisible(myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));
    myChooseActivityStep.setVisible(myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    myActivityParameterStep.setVisible(myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  public void createProject(@NotNull final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        createProject(myWizardState, project, myAssetGenerator);
      }
    });
  }

  public static void createProject(@NotNull final NewModuleWizardState wizardState, @NotNull Project project,
                                   @Nullable AssetStudioAssetGenerator assetGenerator) {
    List<String> errors = Lists.newArrayList();
    try {
      wizardState.populateDirectoryParameters();
      String moduleName = wizardState.getString(FormFactorUtils.ATTR_MODULE_NAME);
      String projectName = wizardState.getString(TemplateMetadata.ATTR_APP_TITLE);
      File projectRoot = new File(wizardState.getString(NewModuleWizardState.ATTR_PROJECT_LOCATION));
      File moduleRoot = new File(projectRoot, moduleName);
      if (FileUtilRt.createDirectory(projectRoot)) {
        if (wizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS) && assetGenerator != null) {
          assetGenerator.outputImagesIntoDefaultVariant(moduleRoot);
        }
        wizardState.updateParameters();
        wizardState.updateDependencies();

        // If this is a new project, instantiate the project-level files
        if (wizardState instanceof NewProjectWizardState) {
          Template projectTemplate = ((NewProjectWizardState)wizardState).myProjectTemplate;
          // @formatter:off
          final RenderingContext projectContext = RenderingContext.Builder.newContext(projectTemplate, project)
            .withOutputRoot(projectRoot)
            .withModuleRoot(moduleRoot)
            .withParams(wizardState.myParameters)
            .build();
          // @formatter:on
          projectTemplate.render(projectContext);
          ConfigureAndroidProjectPath.setGradleWrapperExecutable(projectRoot);
        }

        final RenderingContext context = RenderingContext.Builder.newContext(wizardState.myTemplate, project)
          .withOutputRoot(projectRoot).withModuleRoot(moduleRoot).withParams(wizardState.myParameters).build();
        wizardState.myTemplate.render(context);
        if (wizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY)) {
          TemplateWizardState activityTemplateState = wizardState.getActivityTemplateState();
          activityTemplateState.populateRelativePackage(null);
          Template template = activityTemplateState.getTemplate();
          assert template != null;
          // @formatter:off
          final RenderingContext activityContext = RenderingContext.Builder.newContext(template, project)
            .withOutputRoot(moduleRoot)
            .withModuleRoot(moduleRoot)
            .withParams(activityTemplateState.myParameters)
            .build();
          // @formatter:on
          template.render(activityContext);
          context.getFilesToOpen().addAll(activityContext.getFilesToOpen());
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return;
        }
        GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();

        LanguageLevel initialLanguageLevel = null;
        Object version = wizardState.hasAttr(ATTR_JAVA_VERSION) ? wizardState.get(ATTR_JAVA_VERSION) : null;
        if (version != null) {
          initialLanguageLevel = LanguageLevel.parse(version.toString());
        }
        projectImporter.importNewlyCreatedProject(projectName, projectRoot, new NewProjectImportGradleSyncListener() {
          @Override
          public void syncSucceeded(@NotNull final Project project) {
            // Open files -- but wait until the Android facets are available, otherwise for example
            // the layout editor won't add Design tabs to the file
            StartupManagerEx manager = StartupManagerEx.getInstanceEx(project);
            if (!manager.postStartupActivityPassed()) {
              manager.registerPostStartupActivity(new Runnable() {
                @Override
                public void run() {
                  openTemplateFiles(project);
                }
              });
            }
            else {
              openTemplateFiles(project);
            }
          }

          private boolean openTemplateFiles(Project project) {
            return TemplateUtils.openEditors(project, context.getFilesToOpen(), true);
          }
        }, project, initialLanguageLevel);
      } else {
        errors.add(String.format(UNABLE_TO_CREATE_DIR_FORMAT, projectRoot.getPath()));
      }
    }
    catch (Exception e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
      }
      Messages.showErrorDialog(e.getMessage(), ERROR_MSG_TITLE);
      LOG.error(e);
    }
    if (!errors.isEmpty()) {
      String msg = errors.size() == 1 ? errors.get(0) : Joiner.on('\n').join(errors);
      Messages.showErrorDialog(msg, ERROR_MSG_TITLE);
      LOG.error(msg);
    }
  }
}
