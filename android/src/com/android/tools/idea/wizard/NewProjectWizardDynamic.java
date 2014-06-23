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
package com.android.tools.idea.wizard;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.Lists;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.ConfigureAndroidProjectStep.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.ConfigureAndroidProjectStep.PROJECT_LOCATION_KEY;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Presents a wizard to the user to create a new project.
 */
public class NewProjectWizardDynamic extends DynamicWizard {

  public static final Key<String> GRADLE_VERSION_KEY = createKey(TemplateMetadata.ATTR_GRADLE_VERSION, WIZARD, String.class);
  public static final Key<String> GRADLE_PLUGIN_VERSION_KEY = createKey(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, WIZARD, String.class);
  public static final Key<Boolean> USE_PER_MODULE_REPOS_KEY = createKey(TemplateMetadata.ATTR_PER_MODULE_REPOS, WIZARD, Boolean.class);
  public static final Key<Boolean> IS_NEW_PROJECT_KEY = createKey(ATTR_IS_NEW_PROJECT, WIZARD, Boolean.class);
  public static final Key<Boolean> IS_GRADLE_PROJECT_KEY = createKey(ATTR_IS_GRADLE, WIZARD, Boolean.class);
  public static final Key<String> SDK_DIR_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);
  public static final Key<String> MAVEN_URL_KEY = createKey(ATTR_MAVEN_URL, WIZARD, String.class);
  public static final Key<String> DEBUG_KEYSTORE_SHA_1_KEY = createKey(ATTR_DEBUG_KEYSTORE_SHA1, WIZARD, String.class);

  private static final String ERROR_MSG_TITLE = "Error in New Project Wizard";
  private final List<File> myFilesToOpen = Lists.newArrayList();

  public NewProjectWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module, "New Project");
    setTitle("Create New Project");
  }

  @Override
  public void init() {
    if (!AndroidSdkUtils.isAndroidSdkAvailable() || !TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg =
        "<html>Your Android SDK is missing, out of date, or is missing templates.<br>" +
        "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      super.init();
      Messages.showErrorDialog(msg, title);
      throw new IllegalStateException(msg);
    }
    addPath(new ConfigureAndroidProjectPath(getDisposable()));
    for (NewFormFactorModulePath path : NewFormFactorModulePath.getAvailableFormFactorModulePaths(getDisposable())) {
      addPath(path);
    }
    ScopedStateStore state = getState();
    state.put(GRADLE_VERSION_KEY, GradleUtil.GRADLE_LATEST_VERSION);
    state.put(GRADLE_PLUGIN_VERSION_KEY, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    state.put(USE_PER_MODULE_REPOS_KEY, false);
    state.put(IS_NEW_PROJECT_KEY, true);
    state.put(IS_GRADLE_PROJECT_KEY, true);
    try {
      state.put(DEBUG_KEYSTORE_SHA_1_KEY, KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      LOG.error("Could not create default debug keystore: " + e.getMessage());
      state.put(DEBUG_KEYSTORE_SHA_1_KEY, "");
    }
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData != null) {
      state.put(SDK_DIR_KEY, sdkData.getLocation().getPath());
    }
    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
    if (mavenUrl != null) {
      state.put(MAVEN_URL_KEY, mavenUrl);
    }
    super.init();
  }

  @Override
  protected String getWizardActionDescription() {
    return String.format("Create %1$s", getState().get(APPLICATION_NAME_KEY));
  }

  @Override
  public void performFinishingActions() {
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

    String projectName = getState().get(APPLICATION_NAME_KEY);
    if (projectName == null) {
      projectName = "Unnamed Project";
    }

    // Collect files to open
    for (DynamicWizardPath path : myPaths) {
      if (path instanceof NewFormFactorModulePath) {
        myFilesToOpen.addAll(((NewFormFactorModulePath)path).getFilesToOpen());
      }
    }

    try {
      projectImporter.importProject(projectName, rootLocation, new NewProjectImportGradleSyncListener() {
        @Override
        public void syncEnded(@NotNull final Project project) {
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
          return TemplateUtils.openEditors(project, myFilesToOpen, true);
        }
      });
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
}
