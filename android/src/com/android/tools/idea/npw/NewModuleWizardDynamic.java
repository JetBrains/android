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
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription.WizardStepHeaderSettings;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.wizard.WizardConstants.*;

/**
 * {@linkplain NewModuleWizardDynamic} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizardDynamic extends DynamicWizard {
  public NewModuleWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module, "New Module");
    setTitle("Create New Module");
  }

  @Override
  public void init() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg = "<html>Your Android SDK is missing, out of date, or is missing templates.<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);
      return;
    }
    addPaths();
    ConfigureAndroidProjectPath.putSdkDependentParams(getState());
    initState();
    super.init();
  }

  /**
   * Populate our state store with some common configuration items, such as the SDK location and the Gradle configuration.
   */
  protected void initState() {
    ScopedStateStore state = getState();
    Project project = getProject();

    // TODO(jbakermalone): move the setting of this state closer to where it is used, so it's clear what's needed.
    state.put(WizardConstants.GRADLE_VERSION_KEY, GRADLE_LATEST_VERSION);
    state.put(WizardConstants.GRADLE_PLUGIN_VERSION_KEY, determineGradlePluginVersion(project));
    state.put(WizardConstants.USE_PER_MODULE_REPOS_KEY, false);
    state.put(WizardConstants.IS_NEW_PROJECT_KEY, true);
    state.put(WizardConstants.IS_GRADLE_PROJECT_KEY, true);
    try {
      state.put(WizardConstants.DEBUG_KEYSTORE_SHA_1_KEY, KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      LOG.error("Could not create default debug keystore: " + e.getMessage());
      state.put(WizardConstants.DEBUG_KEYSTORE_SHA_1_KEY, "");
    }
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData != null) {
      state.put(WizardConstants.SDK_DIR_KEY, sdkData.getLocation().getPath());
    }
    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
    if (mavenUrl != null) {
      state.put(WizardConstants.MAVEN_URL_KEY, mavenUrl);
    }
    if (project != null) {
      state.put(PROJECT_LOCATION_KEY, project.getBasePath());
    }

    state.put(FILES_TO_OPEN_KEY, Lists.<File>newArrayList());
  }

  @NotNull
  private static String determineGradlePluginVersion(@Nullable Project project) {
    if (project != null) {
      FullRevision versionInUse = GradleUtil.getAndroidGradleModelVersionInUse(project);
      if (versionInUse != null) {
        return versionInUse.toString();
      }

      FullRevision versionFromBuildFile = GradleUtil.getAndroidGradleModelVersionFromBuildFile(project);
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

  protected void addPaths() {
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
    Project project = getProject();
    if (project == null) {
      return;
    }

    GradleProjectImporter.getInstance().requestProjectSync(project, new NewProjectImportGradleSyncListener() {
      @Override
      public void syncSucceeded(@NotNull final Project project) {
          openTemplateFiles(project);
      }

      private boolean openTemplateFiles(Project project) {
        List<File> filesToOpen = myState.get(FILES_TO_OPEN_KEY);
        assert filesToOpen != null; // Always initialized in initState
        return TemplateUtils.openEditors(project, filesToOpen, true);
      }
    });
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Creating module...";
  }
}
