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

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.Map;

import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for the NewProjectWizard
 * Deprecated by {@link TemplateValueInjector}
 */
@Deprecated
public class NewProjectWizardState {
  public static final String ATTR_PROJECT_LOCATION = "projectLocation";

  private static final String APPLICATION_NAME = "My Application";

  /**
   * State for the template wizard, used to embed an activity template
   */
  private final TemplateWizardState myActivityState;
  private final TemplateWizardState myModuleState;
  private final Template myProjectTemplate;

  public NewProjectWizardState(@NotNull Template moduleTemplate) {
    myModuleState = new TemplateWizardState(moduleTemplate);
    myActivityState = new TemplateWizardState();

    myModuleState.myHidden.add(ATTR_PROJECT_LOCATION);
    myModuleState.myHidden.remove(ATTR_IS_LIBRARY_MODULE);

    myModuleState.put(ATTR_IS_LAUNCHER, false);
    myModuleState.put(ATTR_CREATE_ICONS, false);
    myModuleState.put(ATTR_IS_NEW_PROJECT, true);
    myModuleState.put(ATTR_THEME_EXISTS, true);
    myModuleState.put(ATTR_CREATE_ACTIVITY, true);
    myModuleState.put(ATTR_IS_LIBRARY_MODULE, false);

    putSdkDependentParams();

    try {
      myActivityState.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      Logger.getInstance(NewProjectWizardState.class).info("Could not compute SHA1 hash of debug keystore.", e);
    }

    myActivityState.myHidden.add(ATTR_PACKAGE_NAME);
    myActivityState.myHidden.add(ATTR_APP_TITLE);
    myActivityState.myHidden.add(ATTR_MIN_API);
    myActivityState.myHidden.add(ATTR_MIN_API_LEVEL);
    myActivityState.myHidden.add(ATTR_TARGET_API);
    myActivityState.myHidden.add(ATTR_TARGET_API_STRING);
    myActivityState.myHidden.add(ATTR_BUILD_API);
    myActivityState.myHidden.add(ATTR_BUILD_API_STRING);
    myActivityState.myHidden.add(ATTR_COPY_ICONS);
    myActivityState.myHidden.add(ATTR_IS_LAUNCHER);
    myActivityState.myHidden.add(ATTR_PARENT_ACTIVITY_CLASS);
    myActivityState.myHidden.add(ATTR_ACTIVITY_TITLE);

    updateParameters();

    myModuleState.myHidden.remove(ATTR_PROJECT_LOCATION);
    myModuleState.myHidden.remove(ATTR_IS_LIBRARY_MODULE);
    myModuleState.myHidden.remove(ATTR_APP_TITLE);

    myModuleState.put(ATTR_IS_LIBRARY_MODULE, false);
    myModuleState.put(ATTR_IS_LAUNCHER, true);
    myModuleState.put(ATTR_PROJECT_LOCATION, WizardUtils.getProjectLocationParent().getPath());
    myModuleState.put(ATTR_APP_TITLE, APPLICATION_NAME);
    final int DEFAULT_MIN = SdkVersionInfo.LOWEST_ACTIVE_API;
    myModuleState.put(ATTR_MIN_API_LEVEL, DEFAULT_MIN);
    myModuleState.put(ATTR_MIN_API, Integer.toString(DEFAULT_MIN));
    myProjectTemplate = Template.createFromName(CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);
    myModuleState.setParameterDefaults();

    updateParameters();
  }

  @TestOnly
  public Template getProjectTemplate() {
    return myProjectTemplate;
  }

  @NotNull
  public TemplateWizardState getActivityTemplateState() {
    return myActivityState;
  }

  @NotNull
  public TemplateWizardState getModuleTemplateState() {
    return myModuleState;
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    myModuleState.put(ATTR_COPY_ICONS, !Boolean.parseBoolean(myModuleState.get(ATTR_CREATE_ICONS).toString()));
    copyParameters(myModuleState.getParameters(), myActivityState.getParameters(), ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_TARGET_API_STRING, ATTR_BUILD_API_STRING,
                   ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS, ATTR_IS_GRADLE,
                   ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_TEST_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT);
  }

  private void putSdkDependentParams() {
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
  }

  private static void copyParameters(@NotNull Map<String, Object> from, @NotNull Map<String, Object> to, String... keys) {
    for (String key : keys) {
      to.put(key, from.get(key));
    }
  }
}
