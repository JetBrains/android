/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.npw.NewProjectWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Helper class that tracks the Project Wizard State (Project template, plus its Module and Activity State)
 * TODO: Refactor code to use {@link com.android.tools.idea.npw.template.TemplateValueInjector}
 * TODO: It's possible that after refactoring, this class is so thin that can be inlined and deleted.
 */
class TestNewProjectWizardState {
  private static final String APPLICATION_NAME = "My Application";
  private final Template myProjectTemplate;
  private final TestTemplateWizardState myModuleState = new TestTemplateWizardState();
  private final TestTemplateWizardState myActivityState = new TestTemplateWizardState();

  TestNewProjectWizardState(@NotNull Template moduleTemplate) {
    myModuleState.myTemplate = moduleTemplate;
    myProjectTemplate = Template.createFromName(CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);

    // ------------------ MODULE STATE ---------------
    myModuleState.put(ATTR_IS_GRADLE, true);
    myModuleState.put(ATTR_HAS_APPLICATION_THEME, true);
    myModuleState.put(ATTR_IS_LAUNCHER, true);
    myModuleState.put(ATTR_CREATE_ICONS, false);
    myModuleState.put(ATTR_IS_NEW_PROJECT, true);
    myModuleState.put(ATTR_THEME_EXISTS, true);
    myModuleState.put(ATTR_CREATE_ACTIVITY, true);
    myModuleState.put(ATTR_IS_LIBRARY_MODULE, false);

    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(getClass()), false);
    if (buildTool != null) {
      // If buildTool is null, the template will use buildApi instead, which might be good enough.
      myModuleState.put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    File location = sdkHandler.getLocation();
    if (location != null) {
      // Gradle expects a platform-neutral path
      myModuleState.put(ATTR_SDK_DIR, FileUtil.toSystemIndependentName(location.getPath()));
    }

    myModuleState.put(ATTR_PROJECT_LOCATION, WizardUtils.getProjectLocationParent().getPath());
    myModuleState.put(ATTR_APP_TITLE, APPLICATION_NAME);
    final int DEFAULT_MIN = SdkVersionInfo.LOWEST_ACTIVE_API;
    myModuleState.put(ATTR_MIN_API_LEVEL, DEFAULT_MIN);
    myModuleState.put(ATTR_MIN_API, Integer.toString(DEFAULT_MIN));
    myModuleState.setParameterDefaults();

    // ------------------ ACTIVITY STATE ---------------
    try {
      myActivityState.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      Logger.getInstance(TestNewProjectWizardState.class).info("Could not compute SHA1 hash of debug keystore.", e);
    }

    updateParameters();
  }

  @NotNull
  public TestTemplateWizardState getActivityTemplateState() {
    return myActivityState;
  }

  public TestTemplateWizardState getModuleTemplateState() {
    return myModuleState;
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    myModuleState.put(ATTR_COPY_ICONS, !Boolean.parseBoolean(myModuleState.get(ATTR_CREATE_ICONS).toString()));
    myActivityState.getParameters().putAll(myModuleState.getParameters());
  }

  public Template getProjectTemplate() {
    return myProjectTemplate;
  }
}
