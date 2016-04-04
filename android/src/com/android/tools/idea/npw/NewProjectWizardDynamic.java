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
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PROJECT_LOCATION_KEY;

/**
 * Presents a wizard to the user to create a new project.
 */
public class NewProjectWizardDynamic extends DynamicWizard {

  private static final String ERROR_MSG_TITLE = "Error in New Project Wizard";
  private Project myProject;

  public NewProjectWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module, "New Project");
    setTitle("Create New Project");
  }

  public NewProjectWizardDynamic(@Nullable Project project,
                                 @Nullable Module module,
                                 @NotNull DynamicWizardHost host) {
    super(project, module, "New Project", host);
    setTitle("Create New Project");
  }

  @Override
  public void init() {
    checkSdk();
    addPaths();
    initState();
    super.init();
  }

  protected void checkSdk() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg = "<html>Your Android SDK is missing, out of date, or is missing templates.<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);
      throw new IllegalStateException("Android SDK missing");
    }
  }

  /**
   * Add the steps for this wizard
   */
  private void addPaths() {
    addPath(new ConfigureAndroidProjectPath(getDisposable()));
    for (NewFormFactorModulePath path : NewFormFactorModulePath.getAvailableFormFactorModulePaths(getDisposable())) {
      addPath(path);
    }
  }

  /**
   * Populate our state store with some common configuration items, such as the SDK location and the Gradle configuration.
   */
  private void initState() {
    initState(getState(), SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION);
  }

  static void initState(@NotNull ScopedStateStore state, @NotNull String gradlePluginVersion) {
    state.put(WizardConstants.GRADLE_PLUGIN_VERSION_KEY, gradlePluginVersion);
    state.put(WizardConstants.GRADLE_VERSION_KEY, SdkConstants.GRADLE_LATEST_VERSION);
    state.put(WizardConstants.IS_GRADLE_PROJECT_KEY, true);
    state.put(WizardConstants.IS_NEW_PROJECT_KEY, true);
    state.put(WizardConstants.TARGET_FILES_KEY, new HashSet<File>());
    state.put(WizardConstants.FILES_TO_OPEN_KEY, new ArrayList<File>());
    state.put(WizardConstants.USE_PER_MODULE_REPOS_KEY, false);

    try {
      state.put(WizardConstants.DEBUG_KEYSTORE_SHA_1_KEY, KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception exception) {
      LOG.warn("Could not create debug keystore", exception);
      state.put(WizardConstants.DEBUG_KEYSTORE_SHA_1_KEY, "");
    }

    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);

    if (mavenUrl != null) {
      state.put(WizardConstants.MAVEN_URL_KEY, mavenUrl);
    }

    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();

    if (data != null) {
      state.put(WizardConstants.SDK_DIR_KEY, data.getLocation().getPath());
    }
  }

  @Override
  protected String getWizardActionDescription() {
    return String.format("Create %1$s", getState().get(APPLICATION_NAME_KEY));
  }

  @Override
  public void performFinishingActions() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        runFinish();
      }
    });
  }

  private void runFinish() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
    String rootPath = getState().get(PROJECT_LOCATION_KEY);
    if (rootPath == null) {
      LOG.error("No root path specified for project");
      return;
    }
    File rootLocation = new File(rootPath);

    File wrapperPropertiesFilePath = GradleUtil.getGradleWrapperPropertiesFilePath(rootLocation);
    try {
      GradleUtil.updateGradleDistributionUrl(SdkConstants.GRADLE_LATEST_VERSION, wrapperPropertiesFilePath);
    }
    catch (IOException e) {
      // Unlikely to happen. Continue with import, the worst-case scenario is that sync fails and the error message has a "quick fix".
      LOG.warn("Failed to update Gradle wrapper file", e);
    }

    String projectName = getState().get(APPLICATION_NAME_KEY);
    if (projectName == null) {
      projectName = "Unnamed Project";
    }

    // Pick the highest language level of all the modules/form factors.
    // We have to pick the language level up front while creating the project rather than
    // just reacting to it during sync, because otherwise the user gets prompted with
    // a changing-language-level-requires-reopening modal dialog box and have to reload
    // the project
    LanguageLevel initialLanguageLevel = null;
    Iterator<FormFactor> iterator = FormFactor.iterator();
    while (iterator.hasNext()) {
      FormFactor factor = iterator.next();
      Object version = getState().get(FormFactorUtils.getLanguageLevelKey(factor));
      if (version != null) {
        LanguageLevel level = LanguageLevel.parse(version.toString());
        if (level != null && (initialLanguageLevel == null || level.isAtLeast(initialLanguageLevel))) {
          initialLanguageLevel = level;
        }
      }
    }

    // This is required for Android plugin in IDEA
    if (!AndroidStudioInitializer.isAndroidStudio()) {
      final Sdk jdk = IdeSdks.getJdk();
      if (jdk != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            ProjectRootManager.getInstance(myProject).setProjectSdk(jdk);
          }
        });
      }
    }
    try {
      GradleSyncListener listener = new PostStartupGradleSyncListener(new Runnable() {
        @Override
        public void run() {
          Iterable<File> targetFiles = myState.get(WizardConstants.TARGET_FILES_KEY);
          assert targetFiles != null;

          TemplateUtils.reformatAndRearrange(myProject, targetFiles);

          Collection<File> filesToOpen = myState.get(WizardConstants.FILES_TO_OPEN_KEY);
          assert filesToOpen != null;

          TemplateUtils.openEditors(myProject, filesToOpen, true);
        }
      });

      projectImporter.importNewlyCreatedProject(projectName, rootLocation, listener, myProject, initialLanguageLevel);
    }
    catch (IOException e) {
      Messages.showErrorDialog(e.getMessage(), ERROR_MSG_TITLE);
      LOG.error(e);
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(e.getMessage(), ERROR_MSG_TITLE);
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Creating project...";
  }

  @Override
  protected void doFinish() throws IOException {
    final String location = myState.get(PROJECT_LOCATION_KEY);
    final String name = myState.get(APPLICATION_NAME_KEY);
    assert location != null && name != null;
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        VfsUtil.createDirectoryIfMissing(location);
      }
    }.execute();
    myProject = UIUtil.invokeAndWaitIfNeeded(new Computable<Project>() {
      @Override
      public Project compute() {
        return ProjectManager.getInstance().createProject(name, location);
      }
    });
    super.doFinish();
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }
}
