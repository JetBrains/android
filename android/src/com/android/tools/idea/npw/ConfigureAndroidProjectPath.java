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
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.sdk.wizard.legacy.SmwOldApiDirectInstall;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription.WizardStepHeaderSettings;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * A path to configure global details of a new Android project
 */
public class ConfigureAndroidProjectPath extends DynamicWizardPath {
  private static final Logger LOG = Logger.getInstance(ConfigureAndroidProjectPath.class);

  @NotNull private final Disposable myParentDisposable;

  public ConfigureAndroidProjectPath(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  @Override
  protected void init() {
    putSdkDependentParams(myState);

    addStep(new ConfigureAndroidProjectStep(myParentDisposable));
    addStep(new ConfigureFormFactorStep(myParentDisposable));
    addStep(new LicenseAgreementStep(myParentDisposable));
    addStep(new SmwOldApiDirectInstall(myParentDisposable));
  }

  @Override
  public boolean validate() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      setErrorHtml("<html>Your Android SDK is missing, out of date, or is missing templates. " +
                   "Please ensure you are using SDK version " + VersionCheck.MIN_TOOLS_REV + " or later.<br>" +
                   "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>");
      return false;
    }
    else {
      return true;
    }
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Configure Android Project";
  }

  /**
   * Populate the given state with a set of variables that depend on the user's installed SDK. This method should
   * be called early in the initialization of a wizard or path.
   * Variables:
   * Build Tools Version: Used to populate the project level build.gradle with the correct Gradle plugin version number
   * If the required build tools version is not installed, a request is added for installation
   * SDK Home: The location of the installed SDK
   *
   * @param state the state store to populate with the values stored in the SDK
   */
  public static void putSdkDependentParams(@NotNull ScopedStateStore state) {
    final AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(ConfigureAndroidProjectPath.class));
    Revision minimumRequiredBuildToolVersion = Revision.parseRevision(SdkConstants.MIN_BUILD_TOOLS_VERSION);
    if (buildTool != null && buildTool.getRevision().compareTo(minimumRequiredBuildToolVersion) >= 0) {
      state.put(WizardConstants.BUILD_TOOLS_VERSION_KEY, buildTool.getRevision().toString());
    }
    else {
      // We need to install a new build tools version
      state.listPush(WizardConstants.INSTALL_REQUESTS_KEY, DetailsTypes.getBuildToolsPath(minimumRequiredBuildToolVersion));
      state.put(WizardConstants.BUILD_TOOLS_VERSION_KEY, minimumRequiredBuildToolVersion.toString());
    }

    File location = sdkHandler.getLocation();
    if (location != null) {
      // Gradle expects a platform-neutral path
      state.put(WizardConstants.SDK_HOME_KEY, FileUtil.toSystemIndependentName(location.getPath()));
    }
  }

  @Override
  public boolean canPerformFinishingActions() {
    return performFinishingOperation(true);
  }

  @Override
  public boolean performFinishingActions() {
    try {
      if (!performFinishingOperation(false)) {
        return false;
      }

      Project project = getProject();
      assert project != null;

      File projectRoot = VfsUtilCore.virtualToIoFile(project.getBaseDir());
      setGradleWrapperExecutable(projectRoot);
      return true;
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
  }

  private boolean performFinishingOperation(boolean dryRun) {
    Project project = getProject();
    assert project != null;

    Template projectTemplate = Template.createFromName(Template.CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);
    // @formatter:off
      final RenderingContext context = RenderingContext.Builder.newContext(projectTemplate, project)
        .withCommandName("New Project")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withParams(myState.flatten())
        .intoTargetFiles(myState.get(WizardConstants.TARGET_FILES_KEY))
        .build();
      // @formatter:on
    return projectTemplate.render(context);
  }

  /**
   * Create a header banner for steps in this path.
   */
  protected static WizardStepHeaderSettings buildConfigurationHeader() {
    return WizardStepHeaderSettings.createProductHeader("New Project");
  }

  /**
   * Set the executable bit on the 'gradlew' wrapper script on Mac/Linux
   * On Windows, we use a separate gradlew.bat file which does not need an
   * executable bit.
   *
   * @throws IOException
   */
  public static void setGradleWrapperExecutable(File projectRoot) throws IOException {
    if (SystemInfo.isUnix) {
      File gradlewFile = new File(projectRoot, "gradlew");
      if (!gradlewFile.isFile()) {
        LOG.error("Could not find gradle wrapper. Command line builds may not work properly.");
      }
      else {
        FileUtil.setExecutableAttribute(gradlewFile.getPath(), true);
      }
    }
  }
}
