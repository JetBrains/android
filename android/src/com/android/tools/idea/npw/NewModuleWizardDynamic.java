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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription.WizardStepHeaderSettings;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.SELECTED_MODULE_TYPE_KEY;

/**
 * {@linkplain NewModuleWizardDynamic} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizardDynamic extends DynamicWizard {
  public NewModuleWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module, "New Module");
    setTitle("Create New Module");
  }

  public NewModuleWizardDynamic(@Nullable Project project,
                                @Nullable Module module,
                                @NotNull DynamicWizardHost host) {
    super(project, module, "New Module", host);
    setTitle("Create New Module");
  }

  @Override
  public void init() {
    if (!checkSdk()) return;
    addPaths();
    ConfigureAndroidProjectPath.putSdkDependentParams(getState());
    initState();
    super.init();
  }

  protected boolean checkSdk() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg = "<html>Your Android SDK is missing, out of date, or is missing templates.<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);
      return false;
    }
    return true;
  }

  /**
   * Populate our state store with some common configuration items, such as the SDK location and the Gradle configuration.
   */
  private void initState() {
    ScopedStateStore state = getState();
    Project project = getProject();

    NewProjectWizardDynamic.initState(state, determineGradlePluginVersion(project));

    if (project != null) {
      state.put(WizardConstants.PROJECT_LOCATION_KEY, project.getBasePath());
    }
  }

  @NotNull
  private static String determineGradlePluginVersion(@Nullable Project project) {
    if (project != null) {
      GradleVersion versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project);
      if (versionInUse != null) {
        return versionInUse.toString();
      }

      GradleVersion versionFromBuildFile = GradleUtil.getAndroidGradleModelVersionFromBuildFile(project);
      if (versionFromBuildFile != null) {
        return versionFromBuildFile.toString();
      }
    }
    return SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
  }

  @NotNull
  protected static WizardStepHeaderSettings buildHeader() {
    return WizardStepHeaderSettings.createProductHeader("New Module");
  }

  private void addPaths() {
    Collection<NewModuleDynamicPath> contributions = getContributedPaths();
    Iterable<ModuleTemplateProvider> templateProviders =
      Iterables.concat(ImmutableSet.of(new AndroidModuleTemplatesProvider()), contributions);
    addPath(new SingleStepPath(new ChooseModuleTypeStep(templateProviders, getDisposable())));
    for (NewFormFactorModulePath path : NewFormFactorModulePath.getAvailableFormFactorModulePaths(getDisposable())) {
      path.setGradleSyncIfNecessary(false);
      addPath(path);
    }
    for (NewModuleDynamicPath contribution : contributions) {
      addPath(contribution);
    }
  }

  private Collection<NewModuleDynamicPath> getContributedPaths() {
    ImmutableSet.Builder<NewModuleDynamicPath> builder = ImmutableSet.builder();
    for (NewModuleDynamicPathFactory factory : NewModuleDynamicPathFactory.EP_NAME.getExtensions()) {
      builder.addAll(factory.createWizardPaths(getProject(), getDisposable()));
    }
    return builder.build();
  }

  @Override
  protected String getWizardActionDescription() {
    String applicationName = getState().get(APPLICATION_NAME_KEY);
    if (StringUtil.isEmptyOrSpaces(applicationName)) {
      ModuleTemplate moduleType = myState.get(SELECTED_MODULE_TYPE_KEY);
      applicationName = moduleType != null ? moduleType.getName() : "Module";
    }
    return String.format("Create %1$s", applicationName);
  }

  @Override
  public void performFinishingActions() {
    final Project project = getProject();

    if (project == null) {
      return;
    }

    GradleProjectImporter.getInstance().requestProjectSync(project, new PostStartupGradleSyncListener(new Runnable() {
      @Override
      public void run() {
        Collection<File> filesToOpen = myState.get(WizardConstants.FILES_TO_OPEN_KEY);
        assert filesToOpen != null;

        TemplateUtils.openEditors(project, filesToOpen, true);
      }
    }));
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Creating module...";
  }
}
