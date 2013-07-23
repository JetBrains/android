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
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  public static final String GRADLE_WRAPPER_PROPERTIES_PATH = "gradle/wrapper/gradle-wrapper.properties";
  protected static final String MAVEN_URL_PROPERTY = "android.mavenRepoUrl";

  protected Project myProject;

  public TemplateWizard(String title, Project project) {
    super(title, project);
    myProject = project;
  }

  /**
   * Subclasses and step classes can call this to update next/previous button state; this is
   * generally called after parameter validation has finished.
   */
  public void update() {
    updateButtons();
  }

  @Override
  protected void init() {
    super.init();
    int currentStep = getCurrentStep();
    if (currentStep >= mySteps.size()) {
      return;
    }
    TemplateWizardStep step = (TemplateWizardStep)mySteps.get(currentStep);
    step.getPreferredFocusedComponent();
    if (step != null) {
      step.update();
    }
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

  public Icon getSidePanelIcon() {
    return AndroidIcons.Wizards.NewModuleSidePanel;
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
