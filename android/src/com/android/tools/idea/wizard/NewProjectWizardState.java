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

import com.android.tools.idea.templates.Template;
import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link NewProjectWizard}
 */
public class NewProjectWizardState extends NewModuleWizardState {
  public static final String ATTR_LIBRARY = "isLibrary";
  public static final String ATTR_MODULE_NAME = "projectName";

  private static final String LIBRARY_TEMPLATE = "NewAndroidLibrary";
  private static final String APPLICATION_TEMPLATE = "NewAndroidApplication";

  /**
   * Tracks changes to the is-library flag so we can change our template
   */
  private boolean myPreviousLibraryState;

  public NewProjectWizardState() {
    myHidden.remove(ATTR_PROJECT_LOCATION);
    myHidden.remove(ATTR_IS_LIBRARY_MODULE);

    put(ATTR_LIBRARY, false);
    put(ATTR_PROJECT_LOCATION, getProjectFileDirectory());
    updateTemplate();
    setParameterDefaults();

    updateParameters();
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
    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    return userHome.replace('/', File.separatorChar) + File.separator + productName.replace(" ", "") + "Projects";
  }

  /**
   * Updates the template the wizard is using to reflect whether the project is an application or library.
   */
  private void updateTemplate() {
    boolean isLibrary = (Boolean)get(ATTR_LIBRARY);
    if (myTemplate == null || isLibrary != myPreviousLibraryState) {
      myTemplate = Template.createFromName(CATEGORY_PROJECTS, isLibrary? LIBRARY_TEMPLATE : APPLICATION_TEMPLATE);
      myPreviousLibraryState = isLibrary;
    }
  }
}
