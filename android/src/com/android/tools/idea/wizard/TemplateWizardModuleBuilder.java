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

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.NewProjectWizardState.ATTR_MODULE_NAME;

public class TemplateWizardModuleBuilder extends ModuleBuilder implements TemplateWizardStep.UpdateListener, ChooseTemplateStep.TemplateChangeListener {
  private static final Logger LOG = Logger.getInstance("#" + TemplateWizardModuleBuilder.class.getName());

  protected static final String PROJECT_NAME = "Android Project";
  protected static final String MODULE_NAME = "Android Module";
  protected static final String APP_TEMPLATE_NAME = "Android Application";
  protected static final String LIB_TEMPLATE_NAME = "Android Library";

  protected static final Set<String> EXCLUDED_TEMPLATES = ImmutableSet.of(MODULE_NAME, PROJECT_NAME);

  private final TemplateMetadata myMetadata;
  @NotNull private final List<ModuleWizardStep> mySteps;
  @NotNull private final Map<ModuleWizardStep, WizardPath> myStepsToPath = Maps.newHashMap();
  private final NewAndroidModulePath myNewAndroidModulePath;
  @Nullable private Project myProject;
  @NotNull private final WizardPath[] paths;

  NewModuleWizardState myWizardState;
  private TemplateParameterStep myTemplateParameterStep;
  boolean myInitializationComplete = false;

  public TemplateWizardModuleBuilder(@Nullable File templateFile,
                                     @Nullable TemplateMetadata metadata,
                                     @Nullable Project project,
                                     @Nullable Icon sidePanelIcon,
                                     @NotNull List<ModuleWizardStep> steps,
                                     @NotNull Disposable disposable,
                                     boolean hideModuleName) {
    myMetadata = metadata;
    myProject = project;
    mySteps = steps;

    if (project == null) {
      myWizardState = new NewProjectWizardState() {
        @Override
        public void setTemplateLocation(@NotNull File file) {
          super.setTemplateLocation(file);
          update();
        }
      };
    }
    else {
      myWizardState = new NewModuleWizardState() {
        @Override
        public void setTemplateLocation(@NotNull File file) {
          super.setTemplateLocation(file);
          update();
        }
      };
    }
    myWizardState.put(ATTR_IS_LAUNCHER, project == null);
    myWizardState.updateParameters();

    if (templateFile != null) {
      myWizardState.setTemplateLocation(templateFile);
    }
    if (hideModuleName) {
      myWizardState.myHidden.add(ATTR_MODULE_NAME);
    }

    Template.convertApisToInt(myWizardState.getParameters());

    myTemplateParameterStep = new TemplateParameterStep(myWizardState, myProject, null, sidePanelIcon, this);
    myNewAndroidModulePath = new NewAndroidModulePath(myWizardState, this, project, sidePanelIcon, disposable);
    ImportSourceModulePath importSourcesPath =
      new ImportSourceModulePath(myWizardState, new WizardContext(project), disposable, this);

    paths = new WizardPath[] {importSourcesPath, myNewAndroidModulePath};

    addSteps(importSourcesPath);
    mySteps.add(new ChooseAndroidAndJavaSdkStep());
    mySteps.add(myTemplateParameterStep);
    addSteps(myNewAndroidModulePath);

    if (project != null) {
      myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
    }
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GradleUtil.GRADLE_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    update();

    myInitializationComplete = true;
  }

  private void addSteps(WizardPath path) {
    Collection<ModuleWizardStep> steps = path.getSteps();
    mySteps.addAll(steps);
    myStepsToPath.putAll(Maps.toMap(steps, Functions.constant(path)));
  }

  public boolean isStepVisible(ModuleWizardStep step) {
    WizardPath path = myStepsToPath.get(step);
    return path == null ? step.isStepVisible() : path.isStepVisible(step);
  }

  public boolean updateWizardSteps() {
    if (!myInitializationComplete) {
      return false;
    }
    myTemplateParameterStep.setVisible(!myWizardState.myIsAndroidModule);
    for (WizardPath path : paths) {
      path.update();
    }
    return true;
  }

  public void setupModuleBuilder(boolean haveGlobalRepository) {
    // Hide the library checkbox
    myWizardState.myHidden.add(ATTR_IS_LIBRARY_MODULE);

    myWizardState.put(ATTR_PER_MODULE_REPOS, !haveGlobalRepository);

    mySteps.add(0, buildChooseModuleStep(myProject));
  }

  @Nullable
  @Override
  public String getBuilderId() {
    assert myMetadata != null;
    return myMetadata.getTitle();
  }

  @Override
  @NotNull
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    update();
    return mySteps.toArray(new ModuleWizardStep[mySteps.size()]);
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return null;
  }

  @Override
  public void update() {
    if (!myInitializationComplete) {
      return;
    }
    updateWizardSteps();
  }

  @Override
  public void setupRootModel(final @NotNull ModifiableRootModel rootModel) throws ConfigurationException {
    final Project project = rootModel.getProject();

    // in IntelliJ wizard user is able to choose SDK (i.e. for "java library" module), so set it
    if (myJdk != null){
      rootModel.setSdk(myJdk);
    } else {
      rootModel.inheritSdk();
    }
    if (myProject == null) {
      project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  if (myProject == null) {
                    myWizardState.putSdkDependentParams();
                    myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
                    AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator(myWizardState);
                    NewProjectWizard.createProject(myWizardState, project, assetGenerator);
                  }
                  else {
                    createModule();
                  }
                }
              });
            }
          });
        }
      });
    }

  @Override
  @NotNull
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public Icon getBigIcon() {
    return AndroidIcons.Android24;
  }

  @Override
  public Icon getNodeIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public void setName(@NotNull String name) {
    super.setName(name);
    myNewAndroidModulePath.setName(name);
  }

  public void createModule() {
    createModule(true);
  }

  /**
   * Inflate the chosen template to create the module.
   * @param performGradleSync if set to true, a sync will be triggered after the template has been created.
   */
  public void createModule(boolean performGradleSync) {
    for (WizardPath path : paths) {
      path.createModule();
    }
    if (performGradleSync && myProject != null) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return myWizardState.myIsAndroidModule
           ? AndroidSdkType.getInstance().equals(sdkType)
           : sdkType instanceof JavaSdkType;
  }

  @Override
  public void templateChanged(String templateName) {
    myNewAndroidModulePath.templateChanged();
    myWizardState.templateChanged(myProject, templateName);
    // Let the other elements of the wizard update
    for (ModuleWizardStep step : mySteps) {
      step.updateStep();
    }
  }

  /**
   * Create a template chooser step populated with the correct templates for the new modules.
   */
  ChooseTemplateStep buildChooseModuleStep(@Nullable Project project) {
    // We're going to build up our own list of templates here
    // This is a little hacky, we should clean this up later.
    ChooseTemplateStep chooseModuleStep =
      new ChooseTemplateStep(myWizardState, null, project, null, AndroidIcons.Wizards.NewModuleSidePanel,
                             this, this);

    // Get the list of templates to offer, but exclude the NewModule and NewProject template
    List<ChooseTemplateStep.MetadataListItem> templateList =
      ChooseTemplateStep.getTemplateList(myWizardState, CATEGORY_PROJECTS, EXCLUDED_TEMPLATES);

    // Now, we're going to add in two pointers to the same template
    File moduleTemplate = new File(TemplateManager.getTemplateRootFolder(),
                                   FileUtil.join(CATEGORY_PROJECTS, NewProjectWizardState.MODULE_TEMPLATE_NAME));
    TemplateManager manager = TemplateManager.getInstance();
    TemplateMetadata metadata = manager.getTemplate(moduleTemplate);

    ChooseTemplateStep.MetadataListItem appListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return APP_TEMPLATE_NAME;
      }
    };
    ChooseTemplateStep.MetadataListItem libListItem = new ChooseTemplateStep.MetadataListItem(moduleTemplate, metadata) {
      @Override
      public String toString() {
        return LIB_TEMPLATE_NAME;
      }
    };
    templateList.add(0, libListItem);
    templateList.add(0, appListItem);
    chooseModuleStep.setListData(templateList);
    return chooseModuleStep;
  }

}
