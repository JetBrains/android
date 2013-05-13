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

import com.android.sdklib.BuildToolInfo;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link com.android.tools.idea.wizard.NewModuleWizard}
 */
public class NewModuleWizardState extends TemplateWizardState {
  public static final String ATTR_CREATE_ACTIVITY = "createActivity";
  public static final String ATTR_PROJECT_LOCATION = "projectLocation";

  /**
   * State for the template wizard, used to embed an activity template
   */
  protected final TemplateWizardState myActivityTemplateState;

  /**
   * State for the page that lets users create custom launcher icons
   */
  protected final LauncherIconWizardState myLauncherIconState;

  /**
   * True if the module being created is an Android module (as opposed to a generic Java module with no Android support)
   */
  protected boolean myIsAndroidModule;

  public NewModuleWizardState() {
    myActivityTemplateState = new TemplateWizardState();
    myLauncherIconState = new LauncherIconWizardState();

    myHidden.add(ATTR_PROJECT_LOCATION);
    myHidden.add(ATTR_IS_LIBRARY_MODULE);

    put(ATTR_IS_LAUNCHER, true);
    put(ATTR_CREATE_ICONS, true);
    put(ATTR_IS_NEW_PROJECT, true);
    put(ATTR_CREATE_ACTIVITY, true);

    BuildToolInfo buildTool = AndroidSdkUtils.tryToChooseAndroidSdk().getLatestBuildTool();
    if (buildTool != null) {
      // If buildTool is null, the template will use buildApi instead, which might be good enough.
      put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    myActivityTemplateState.myHidden.add(ATTR_PACKAGE_NAME);
    myActivityTemplateState.myHidden.add(ATTR_APP_TITLE);
    myActivityTemplateState.myHidden.add(ATTR_MIN_API);
    myActivityTemplateState.myHidden.add(ATTR_MIN_API_LEVEL);
    myActivityTemplateState.myHidden.add(ATTR_TARGET_API);
    myActivityTemplateState.myHidden.add(ATTR_BUILD_API);
    myActivityTemplateState.myHidden.add(ATTR_COPY_ICONS);
    myActivityTemplateState.myHidden.add(ATTR_IS_LAUNCHER);
    myActivityTemplateState.myHidden.add(ATTR_PARENT_ACTIVITY_CLASS);
    myActivityTemplateState.myHidden.add(ATTR_ACTIVITY_TITLE);

    updateParameters();
  }

  @NotNull
  public TemplateWizardState getActivityTemplateState() {
    return myActivityTemplateState;
  }

  @NotNull
  public LauncherIconWizardState getLauncherIconState() {
    return myLauncherIconState;
  }

  @Override
  public void setTemplateLocation(@NotNull File file) {
    super.setTemplateLocation(file);
    myIsAndroidModule = myTemplate.getMetadata().getParameter(ATTR_MIN_API) != null;
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    put(ATTR_COPY_ICONS, !Boolean.parseBoolean(get(ATTR_CREATE_ICONS).toString()));
    copyParameters(myParameters, myActivityTemplateState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS, ATTR_IS_GRADLE, ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT);
    copyParameters(myParameters, myLauncherIconState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS, ATTR_IS_GRADLE, ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT);
  }

  protected void copyParameters(@NotNull Map<String, Object> from, @NotNull Map<String, Object> to, String... keys) {
    for (String key : keys) {
      to.put(key, from.get(key));
    }
  }
}
