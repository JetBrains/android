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
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.newProject.AndroidModuleBuilder;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.npw.FormFactorUtils.ATTR_MODULE_NAME;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.TemplateMetadata.*;

public class ImportWizardModuleBuilder extends ModuleBuilder
  implements TemplateWizardStep.UpdateListener, ChooseTemplateStep.TemplateChangeListener {

  @NotNull protected final List<ModuleWizardStep> mySteps;
  @NotNull protected final Iterable<WizardPath> myPaths;
  protected final NewModuleWizardState myWizardState;
  @Nullable private final VirtualFile myImportSource;
  @NotNull private final Map<ModuleWizardStep, WizardPath> myStepsToPath = Maps.newHashMap();
  @VisibleForTesting
  protected boolean myInitializationComplete = false;
  @Nullable private Project myProject;
  private ImportSourceModulePath myImportSourcesPath;

  public ImportWizardModuleBuilder(@Nullable File templateFile,
                                   @Nullable Project project,
                                   @Nullable VirtualFile importSource,
                                   @Nullable Icon sidePanelIcon,
                                   @NotNull List<ModuleWizardStep> steps,
                                   @NotNull Disposable disposable,
                                   boolean inGlobalWizard) {
    myProject = project;
    myImportSource = importSource;
    mySteps = steps;

    myWizardState = new NewProjectWizardState() {
      @Override
      public void setTemplateLocation(@NotNull File file) {
        super.setTemplateLocation(file);
        update();
      }
    };

    myWizardState.put(ATTR_IS_LAUNCHER, project == null);
    myWizardState.updateParameters();

    if (templateFile != null) {
      myWizardState.setTemplateLocation(templateFile);
    }
    if (inGlobalWizard) {
      if (myProject != null) {
        myWizardState.myHidden.add(ATTR_MODULE_NAME);
      }
      myWizardState.myHidden.add(ATTR_PROJECT_LOCATION);
    }

    Template.convertApisToInt(myWizardState.getParameters());
    Iterable<WizardPath> paths = setupWizardPaths(project, sidePanelIcon, disposable);
    if (inGlobalWizard) {
      myPaths = Iterables.filter(paths, WizardPath::supportsGlobalWizard);
    }
    else {
      myPaths = paths;
    }

    for (WizardPath path : myPaths) {
      addSteps(path);
    }

    myWizardState.setDefaultWizardPath(getDefaultPath());
    if (project != null) {
      myWizardState.put(ATTR_PROJECT_LOCATION, project.getBasePath());
    }
    myWizardState.put(ATTR_GRADLE_VERSION, GRADLE_LATEST_VERSION);
    myWizardState.put(ATTR_GRADLE_PLUGIN_VERSION, GRADLE_PLUGIN_RECOMMENDED_VERSION);
    update();

    myInitializationComplete = true;
  }

  protected WizardPath getDefaultPath() {
    return myImportSourcesPath;
  }

  protected Iterable<WizardPath> setupWizardPaths(@Nullable Project project, @Nullable Icon sidePanelIcon, Disposable disposable) {
    myImportSourcesPath = new ImportSourceModulePath(myImportSource, myWizardState, new WizardContext(project), sidePanelIcon, this);
    return ImmutableList.of(myImportSourcesPath);
  }

  protected void addSteps(WizardPath path) {
    Collection<ModuleWizardStep> steps = path.getSteps();
    mySteps.addAll(steps);
    myStepsToPath.putAll(Maps.toMap(steps, Functions.constant(path)));
  }

  public boolean isStepVisible(ModuleWizardStep step) {
    WizardPath path = myStepsToPath.get(step);
    return path == null
           ? step.isStepVisible()
           : path == myWizardState.getActiveWizardPath() && path.isStepVisible(step);
  }

  public boolean updateWizardSteps() {
    if (!myInitializationComplete) {
      return false;
    }
    myWizardState.getActiveWizardPath().update();
    return true;
  }

  public void setupModuleBuilder(boolean haveGlobalRepository) {
    // Hide the library checkbox
    myWizardState.myHidden.add(ATTR_IS_LIBRARY_MODULE);

    myWizardState.put(ATTR_PER_MODULE_REPOS, !haveGlobalRepository);
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
    if (myWizardState.hasAttr(ATTR_APP_TITLE)) {
      final String applicationName = myWizardState.getString(ATTR_APP_TITLE);

      if (!applicationName.isEmpty()) {
        settingsStep.getModuleNameField().setText(applicationName.replace(" ", ""));
      }
    }
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
    if (myJdk != null) {
      rootModel.setSdk(myJdk);
    }
    else {
      rootModel.inheritSdk();
    }
    if (myProject == null) {
      project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        DumbService.getInstance(project).smartInvokeLater(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
                  @Override
                  public void run() {
                    if (myProject == null) {
                      myWizardState.putSdkDependentParams();
                      myWizardState.put(ATTR_PROJECT_LOCATION, project.getBasePath());
                      AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator(myWizardState);
                      WizardUtils.createProject(myWizardState, project, assetGenerator);
                    }
                    else {
                      myWizardState.put(ATTR_MODULE_NAME, getName());
                      createModule();
                    }
                  }
                });
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
  protected ProjectType getProjectType() {
    return AndroidModuleBuilder.ANDROID_PROJECT_TYPE;
  }

  @Override
  public Icon getBigIcon() {
    return AndroidIcons.Android24;
  }

  @Override
  public Icon getNodeIcon() {
    return AndroidIcons.Android;
  }

  public void createModule() {
    createModule(true);
  }

  /**
   * Inflate the chosen template to create the module.
   *
   * @param performGradleSync if set to true, a sync will be triggered after the template has been created.
   */
  public void createModule(boolean performGradleSync) {
    WizardPath path = myWizardState.getActiveWizardPath();
    path.createModule();
    if (performGradleSync && myProject != null) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    boolean isJavaTemplate = myWizardState.isOnDefaultWizardPath() &&
                             NewModuleWizardState.isAndroidTemplate(myWizardState.getTemplateMetadata());
    return isJavaTemplate ? sdkType instanceof JavaSdkType : AndroidSdkType.getInstance().equals(sdkType);
  }

  @Override
  public void templateChanged(String templateName) {
    myWizardState.templateChanged(myProject, templateName);
    // Let the other elements of the wizard update
    for (ModuleWizardStep step : mySteps) {
      step.updateStep();
    }
  }

  @NotNull
  public Iterable<WizardPath> getPaths() {
    return myPaths;
  }
}
