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

import com.android.SdkConstants;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizard extends TemplateWizard implements ChooseTemplateStep.TemplateChangeListener {
  protected TemplateWizardModuleBuilder myModuleBuilder;
  protected static final String PROJECT_NAME = "Android Project";
  protected static final String MODULE_NAME = "Android Module";
  protected static final String APP_NAME = "Android Application";
  protected static final String LIB_NAME = "Android Library";

  protected static final Set<String> EXCLUDED_TEMPLATES = ImmutableSet.of(MODULE_NAME, PROJECT_NAME);

  public NewModuleWizard(@Nullable Project project) {
    super("New Module", project);
    Window window = getWindow();
    // Allow creation in headless mode for tests
    if (window != null) {
      getWindow().setMinimumSize(new Dimension(1000, 640));
    } else {
      // We should always have a window unless we're in test mode
      ApplicationManager.getApplication().isUnitTestMode();
    }
    init();
  }

  @Override
  protected void init() {
    myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myProject, AndroidIcons.Wizards.NewModuleSidePanel, mySteps, false) {
      @Override
      public void update() {
        super.update();
        NewModuleWizard.this.update();
      }
    };

    // Hide the library checkbox
    myModuleBuilder.myWizardState.myHidden.add(ATTR_IS_LIBRARY_MODULE);

    boolean haveGlobalRepository = false;
    VirtualFile buildGradle = myProject.getBaseDir().findChild(FN_BUILD_GRADLE);
    if (buildGradle != null) {
      String contents = TemplateUtils.readTextFile(myProject, buildGradle);
      if (contents != null) {
        haveGlobalRepository = contents.contains("repositories") && contents.contains(SdkConstants.GRADLE_PLUGIN_NAME);
      }
    }
    myModuleBuilder.myWizardState.put(ATTR_PER_MODULE_REPOS, !haveGlobalRepository);

    myModuleBuilder.mySteps.add(0, buildChooseModuleStep(myModuleBuilder, myProject, this));
    super.init();
  }

  @Override
  public void update() {
    if (myModuleBuilder == null || !myModuleBuilder.myInitializationComplete) {
      return;
    }
    NewModuleWizardState wizardState = myModuleBuilder.myWizardState;
    myModuleBuilder.myConfigureAndroidModuleStep.setVisible(wizardState.myIsAndroidModule);
    if (wizardState.myIsAndroidModule) {
      myModuleBuilder.myConfigureAndroidModuleStep.updateStep();
    }
    myModuleBuilder.myTemplateParameterStep.setVisible(!wizardState.myIsAndroidModule);
    myModuleBuilder.myAssetSetStep.setVisible(wizardState.myIsAndroidModule &&
                                                  wizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));
    myModuleBuilder.myChooseActivityStep.setVisible(wizardState.myIsAndroidModule &&
                                                    wizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    myModuleBuilder.myActivityTemplateParameterStep.setVisible(wizardState.myIsAndroidModule &&
                                                               wizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  public void createModule() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myModuleBuilder.createModule();
      }
    });
  }

  @Override
  public void templateChanged(String templateName) {
    myModuleBuilder.myConfigureAndroidModuleStep.refreshUiFromParameters();
    if (templateName.equals(LIB_NAME)) {
      myModuleBuilder.myWizardState.put(ATTR_IS_LIBRARY_MODULE, true);
      myModuleBuilder.myWizardState.put(ATTR_IS_LAUNCHER, false);
      myModuleBuilder.myWizardState.put(ATTR_CREATE_ICONS, false);
      // Hide the create icons checkbox
      myModuleBuilder.myWizardState.myHidden.add(ATTR_CREATE_ICONS);
    } else if (templateName.equals(APP_NAME)) {
      myModuleBuilder.myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);
      myModuleBuilder.myWizardState.put(ATTR_IS_LAUNCHER, true);
      myModuleBuilder.myWizardState.put(ATTR_CREATE_ICONS, true);
      // Show the create icons checkbox
      myModuleBuilder.myWizardState.myHidden.remove(ATTR_CREATE_ICONS);
    }

    if (myModuleBuilder.myWizardState.myHidden.contains(ATTR_APP_TITLE)) {
      // If the app title is hidden, set it to the existing app title
      myModuleBuilder.myWizardState.put(ATTR_APP_TITLE, myProject.getName());
    }
    // Let the other elements of the wizard update
    for (ModuleWizardStep step : mySteps) {
      step.updateStep();
    }
  }

  /**
   * Create a template chooser step populated with the correct templates for the new modules.
   */
  protected static ChooseTemplateStep buildChooseModuleStep(@NotNull TemplateWizardModuleBuilder builder,
                                                     @NotNull Project project,
                                                     @Nullable ChooseTemplateStep.TemplateChangeListener listener) {
    // We're going to build up our own list of templates here
    // This is a little hacky, we should clean this up later.
    ChooseTemplateStep chooseModuleStep =
      new ChooseTemplateStep(builder.myWizardState, null, project, null, AndroidIcons.Wizards.NewModuleSidePanel,
                             builder, listener);

    // Get the list of templates to offer, but exclude the NewModule and NewProject template
    List<ChooseTemplateStep.MetadataListItem> templateList =
      chooseModuleStep.getTemplateList(builder.myWizardState, CATEGORY_PROJECTS, EXCLUDED_TEMPLATES);

    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(CATEGORY_PROJECTS, NewProjectWizardState.MODULE_TEMPLATE_NAME));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplate(moduleTemplate);

    ChooseTemplateStep.MetadataListItem appListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return APP_NAME;
      }
    };
    ChooseTemplateStep.MetadataListItem libListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return LIB_NAME;
      }
    };
    templateList.add(0, libListItem);
    templateList.add(0, appListItem);
    chooseModuleStep.setListData(templateList);
    return chooseModuleStep;
  }
}
