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
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.npw.cpp.ConfigureCppSupportPath;
import com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectPath;
import com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep;
import com.android.tools.idea.npw.deprecated.NewFormFactorModulePath;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.*;
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
import com.intellij.openapi.util.SystemInfo;
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

import static com.android.tools.idea.wizard.WizardConstants.*;

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
    addPath(new ConfigureCppSupportPath(getDisposable()));
  }

  /**
   * Populate our state store with some common configuration items, such as the SDK location and the Gradle configuration.
   */
  private void initState() {
    initState(getState(), AndroidPluginGeneration.ORIGINAL.getLatestKnownVersion());
  }

  static void initState(@NotNull ScopedStateStore state, @NotNull String gradlePluginVersion) {
    state.put(GRADLE_PLUGIN_VERSION_KEY, gradlePluginVersion);
    state.put(GRADLE_VERSION_KEY, SdkConstants.GRADLE_LATEST_VERSION);
    state.put(IS_GRADLE_PROJECT_KEY, true);
    state.put(IS_NEW_PROJECT_KEY, true);
    state.put(TARGET_FILES_KEY, new HashSet<>());
    state.put(FILES_TO_OPEN_KEY, new ArrayList<>());
    state.put(USE_PER_MODULE_REPOS_KEY, false);
    state.put(ALSO_CREATE_IAPK_KEY, true);

    // For now, our definition of low memory is running in a 32-bit JVM. In this case, we have to be careful about the amount of memory we
    // request for the Gradle build.
    state.put(WizardConstants.IS_LOW_MEMORY_KEY, SystemInfo.is32Bit);

    try {
      state.put(DEBUG_KEYSTORE_SHA_1_KEY, KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception exception) {
      LOG.warn("Could not create debug keystore", exception);
      state.put(DEBUG_KEYSTORE_SHA_1_KEY, "");
    }

    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);

    if (mavenUrl != null) {
      state.put(MAVEN_URL_KEY, mavenUrl);
    }

    AndroidSdkData data = AndroidSdks.getInstance().tryToChooseAndroidSdk();

    if (data != null) {
      File sdkLocation = data.getLocation();
      state.put(SDK_DIR_KEY, sdkLocation.getPath());

      String espressoVersion = RepositoryUrlManager.get().getLibraryRevision(SupportLibrary.ESPRESSO_CORE.getGroupId(),
                                                                             SupportLibrary.ESPRESSO_CORE.getArtifactId(),
                                                                             null, false, sdkLocation, FileOpUtils.create());

      if (espressoVersion != null) {
        state.put(ESPRESSO_VERSION_KEY, espressoVersion);
      }
    }
  }

  @Override
  protected String getWizardActionDescription() {
    return String.format("Create %1$s", getState().get(APPLICATION_NAME_KEY));
  }

  @Override
  public void performFinishingActions() {
    ApplicationManager.getApplication().invokeLater(this::runFinish);
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
    File wrapperPropertiesFilePath = GradleWrapper.getDefaultPropertiesFilePath(rootLocation);
    try {
      GradleWrapper.get(wrapperPropertiesFilePath).updateDistributionUrl(SdkConstants.GRADLE_LATEST_VERSION);
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
    for (FormFactor factor : FormFactor.values()) {
      Object version = getState().get(FormFactorUtils.getLanguageLevelKey(factor));
      if (version != null) {
        LanguageLevel level = LanguageLevel.parse(version.toString());
        if (level != null && (initialLanguageLevel == null || level.isAtLeast(initialLanguageLevel))) {
          initialLanguageLevel = level;
        }
      }
    }

    // This is required for Android plugin in IDEA
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      final Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(myProject).setProjectSdk(jdk));
      }
    }
    try {
      GradleSyncListener listener = new PostStartupGradleSyncListener(() -> {
        Iterable<File> targetFiles = myState.get(TARGET_FILES_KEY);
        assert targetFiles != null;

        TemplateUtils.reformatAndRearrange(myProject, targetFiles);

        Collection<File> filesToOpen = myState.get(FILES_TO_OPEN_KEY);
        assert filesToOpen != null;

        TemplateUtils.openEditors(myProject, filesToOpen, true);
      });

      GradleProjectImporter.Request request = new GradleProjectImporter.Request();
      request.setLanguageLevel(initialLanguageLevel).setProject(myProject);
      projectImporter.importProject(projectName, rootLocation, request, listener);
    }
    catch (IOException | ConfigurationException e) {
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
  public void doFinishAction() {
    if (!checkFinish()) return;

    final String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    assert projectLocation != null;

    boolean couldEnsureLocationExists = WriteCommandAction.runWriteCommandAction(getProject(), new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        // We generally assume that the path has passed a fair amount of prevalidation checks
        // at the project configuration step before. Write permissions check can be tricky though in some cases,
        // e.g., consider an unmounted device in the middle of wizard execution or changed permissions.
        // Anyway, it seems better to check that we were really able to create the target location and are able to
        // write to it right here when the wizard is about to close, than running into some internal IDE errors
        // caused by these problems downstream
        // Note: this change was originally caused by http://b.android.com/219851, but then
        // during further discussions that a more important bug was in path validation in the old wizards,
        // where File.canWrite() always returned true as opposed to the correct Files.isWritable(), which is
        // already used in new wizard's PathValidator.
        // So the change below is therefore a more narrow case than initially supposed (however it still needs to be handled)
        try {
          if (VfsUtil.createDirectoryIfMissing(projectLocation) != null
              && FileOpUtils.create().canWrite(new File(projectLocation))) {
            return true;
          }
        } catch (Exception e) {
          LOG.error(String.format("Exception thrown when creating target project location: %1$s", projectLocation), e);
        }
        return false;
      }
    });
    if(!couldEnsureLocationExists) {
      Messages.showErrorDialog(String.format("Could not ensure the target project location exists and is accessible:\n\n%1$s\n\n" +
                                             "Please try to specify another path.", projectLocation),
                                             "Error Creating Project");
      navigateToNamedStep(ConfigureAndroidProjectStep.STEP_NAME, true);
      myHost.shakeWindow();
      return;
    }

    // Create project in the dispatch thread. (super.doFinishAction also calls doFinish, but in other thread.)
    try {
      doFinish();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  protected void doFinish() throws IOException {
    final String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    final String name = myState.get(APPLICATION_NAME_KEY);
    assert projectLocation != null && name != null;

    myProject = UIUtil.invokeAndWaitIfNeeded(() -> ProjectManager.getInstance().createProject(name, projectLocation));
    super.doFinish();
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }
}
