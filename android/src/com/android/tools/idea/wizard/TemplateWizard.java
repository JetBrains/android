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
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * TemplateWizard is a base class for Freemarker template-based wizards.
 */
public class TemplateWizard extends AbstractWizard<ModuleWizardStep> {
  public static final String MAIN_FLAVOR_SOURCE_PATH = "src/main";
  public static final String JAVA_SOURCE_PATH = "java";
  public static final String RESOURCE_SOURCE_PATH = "res";
  public static final String GRADLE_WRAPPER_PATH = "gradle/wrapper";
  protected static final String MAVEN_URL_PROPERTY = "android.mavenRepoUrl";

  public TemplateWizard(String title, Project project) {
    super(title, project);
  }

  /**
   * Subclasses and step classes can call this to update next/previous button state; this is
   * generally called after parameter validation has finished.
   */
  void update() {
    updateButtons();
  }

  @Override
  protected boolean canGoNext() {
    return !mySteps.isEmpty() && ((TemplateWizardStep)mySteps.get(getCurrentStep())).isValid();
  }

  @Nullable
  @Override
  protected String getHelpID() {
    return null;
  }

  @Override
  protected final int getNextStep(final int step) {
    for (int i = step + 1; i < mySteps.size(); i++) {
      if (mySteps.get(i).isStepVisible()) {
        return i;
      }
    }
    return step;
  }

  @Override
  protected final int getPreviousStep(final int step) {
    for (int i = step - 1; i >= 0; i--) {
      if (mySteps.get(i).isStepVisible()) {
        return i;
      }
    }
    return step;
  }

  /**
   * Sets a number of parameters that get picked up as globals in the Freemarker templates. These are used to specify the directories where
   * a number of files go. The templates use these globals to allow them to service both old-style Ant builds with the old directory
   * structure and new-style Gradle builds with the new structure.
   */
  protected void populateDirectoryParameters(TemplateWizardState wizardState) throws IOException {
    File projectRoot = new File((String)wizardState.get(NewModuleWizardState.ATTR_PROJECT_LOCATION));
    File moduleRoot = new File(projectRoot, (String)wizardState.get(NewProjectWizardState.ATTR_MODULE_NAME));
    File mainFlavorSourceRoot = new File(moduleRoot, MAIN_FLAVOR_SOURCE_PATH);
    File javaSourceRoot = new File(mainFlavorSourceRoot, JAVA_SOURCE_PATH);
    File javaSourcePackageRoot = new File(javaSourceRoot, ((String)wizardState.get(TemplateMetadata.ATTR_PACKAGE_NAME)).replace('.', '/'));
    File resourceSourceRoot = new File(mainFlavorSourceRoot, RESOURCE_SOURCE_PATH);
    String mavenUrl = System.getProperty(MAVEN_URL_PROPERTY);
    wizardState.put(TemplateMetadata.ATTR_TOP_OUT, projectRoot.getPath());
    wizardState.put(TemplateMetadata.ATTR_PROJECT_OUT, moduleRoot.getPath());
    wizardState.put(TemplateMetadata.ATTR_MANIFEST_OUT, mainFlavorSourceRoot.getPath());
    wizardState.put(TemplateMetadata.ATTR_SRC_OUT, javaSourcePackageRoot.getPath());
    wizardState.put(TemplateMetadata.ATTR_RES_OUT, resourceSourceRoot.getPath());
    if (mavenUrl != null) {
      wizardState.put(TemplateMetadata.ATTR_MAVEN_URL, mavenUrl);
    }
  }

  @Nullable
  protected Sdk getSdk(int apiLevel) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      AndroidPlatform androidPlatform = AndroidPlatform.parse(sdk);
      if (androidPlatform != null) {
        AndroidSdkData sdkData = androidPlatform.getSdkData();
        IAndroidTarget target = sdkData.findTargetByApiLevel(Integer.toString(apiLevel));
        if (target != null) {
          return sdk;
        }
      }
    }
    return null;
  }
}
