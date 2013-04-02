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

import com.android.sdklib.IAndroidTarget;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.SystemProperties;
import com.android.tools.idea.templates.Template;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link NewProjectWizard}
 */
public class NewProjectWizardState extends TemplateWizardState {
  public static final String ATTR_CREATE_ACTIVITY = "createActivity";
  public static final String ATTR_LIBRARY = "isLibrary";
  public static final String ATTR_PROJECT_LOCATION = "projectLocation";
  public static final String ATTR_PROJECT_NAME = "projectName";

  private static final String LIBRARY_TEMPLATE = "NewAndroidLibrary";
  private static final String APPLICATION_TEMPLATE = "NewAndroidApplication";

  /**
   * State for the template wizard, used to embed an activity template
   */
  private final TemplateWizardState myActivityTemplateState;

  /**
   * State for the page that lets users create custom launcher icons
   */
  private final LauncherIconWizardState myLauncherIconState;

  /**
   * Tracks changes to the is-library flag so we can change our template
   */
  private boolean myPreviousLibraryState;

  /**
   * The compilation target to use for this project
   */
  private IAndroidTarget myBuildTarget;

  public NewProjectWizardState() {
    super();
    myActivityTemplateState = new TemplateWizardState();
    myLauncherIconState = new LauncherIconWizardState();

    put(ATTR_LIBRARY, false);
    updateTemplate();
    setParameterDefaults();

    put(ATTR_IS_LAUNCHER, true);
    put(ATTR_CREATE_ICONS, true);
    put(ATTR_IS_NEW_PROJECT, true);
    put(ATTR_CREATE_ACTIVITY, true);
    put(ATTR_PROJECT_LOCATION, getProjectFileDirectory());
    convertToInt(ATTR_MIN_API);
    convertToInt(ATTR_BUILD_API);
    convertToInt(ATTR_MIN_API_LEVEL);
    convertToInt(ATTR_TARGET_API);

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

  public IAndroidTarget getBuildTarget() {
    return myBuildTarget;
  }

  /**
   * Returns the default directory where new projects should go.
   */
  @NotNull
  public static String getProjectFileDirectory() {
    final String lastProjectLocation = GeneralSettings.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    updateTemplate();
    put(ATTR_COPY_ICONS, !Boolean.parseBoolean(get(ATTR_CREATE_ICONS).toString()));
    copyParameters(myParameters, myActivityTemplateState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS);
    copyParameters(myParameters, myLauncherIconState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS);
  }

  /**
   * Updates the template the wizard is using to reflect whether the project is an application or library.
   */
  private void updateTemplate() {
    boolean isLibrary = (Boolean)get(ATTR_LIBRARY);
    if (myTemplate == null || isLibrary != myPreviousLibraryState) {
      myTemplate = Template.createFromName(CATEGORY_PROJECTS, isLibrary? LIBRARY_TEMPLATE : APPLICATION_TEMPLATE);
    }
  }

  private void copyParameters(@NotNull Map<String, Object> from, @NotNull Map<String, Object> to, String... keys) {
    for (String key : keys) {
      to.put(key, from.get(key));
    }
  }

  private void convertToInt(String attr) {
    if (myParameters.containsKey(attr)) {
      put(attr, Integer.parseInt(get(attr).toString()));
    }
  }
}
