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

import com.android.tools.idea.npw.ConfigureAndroidProjectPath;
import com.android.tools.idea.npw.NewModuleWizardState;
import com.android.tools.idea.npw.NewProjectWizard;
import com.android.tools.idea.npw.NewProjectWizardDynamic;
import com.android.tools.idea.templates.Template;
import com.intellij.ide.RecentProjectsManagerImpl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link NewProjectWizard}
 * Deprecated by {@link NewProjectWizardDynamic} and {@link ConfigureAndroidProjectPath}
 */
@Deprecated
public class NewProjectWizardState extends NewModuleWizardState {
  public static final String ATTR_MODULE_NAME = "projectName";

  static final String MODULE_TEMPLATE_NAME = "NewAndroidModule";
  static final String PROJECT_TEMPLATE_NAME = "NewAndroidProject";
  static final String IMPORT_EXISTING_PROJECT_TEMPLATE_NAME = "ImportExistingProject";
  static final String APPLICATION_NAME = "My Application";

  protected Template myProjectTemplate;

  public NewProjectWizardState() {
    myHidden.remove(ATTR_PROJECT_LOCATION);
    myHidden.remove(ATTR_IS_LIBRARY_MODULE);
    myHidden.remove(ATTR_APP_TITLE);

    put(ATTR_IS_LIBRARY_MODULE, false);
    put(ATTR_IS_LAUNCHER, true);
    put(ATTR_PROJECT_LOCATION, getProjectFileDirectory());
    put(ATTR_APP_TITLE, APPLICATION_NAME);
    final int DEFAULT_MIN = 9;
    put(ATTR_MIN_API_LEVEL, DEFAULT_MIN);
    put(ATTR_MIN_API, Integer.toString(DEFAULT_MIN));
    myProjectTemplate = Template.createFromName(CATEGORY_PROJECTS, PROJECT_TEMPLATE_NAME);
    myTemplate = Template.createFromName(CATEGORY_PROJECTS, MODULE_TEMPLATE_NAME);
    setParameterDefaults();

    updateParameters();
  }

  /**
   * Returns the default directory where new projects should go.
   */
  @NotNull
  public static String getProjectFileDirectory() {
    final String lastProjectLocation = RecentProjectsManagerImpl.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }
}
