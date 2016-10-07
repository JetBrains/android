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
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.StepListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * Wraps existing AAR or Jar files into projects.
 */
public class WrapArchiveWizardPath implements WizardPath {
  /**
   * String property with absolute path to source archive file
   */
  protected static final String KEY_ARCHIVE = "newmodule.wrap.archivePath";
  /**
   * String value denoting the Gradle path of the new module. May be a complex path.
   *
   * Note that the path written to settings.gradle will be made absolute.
   */
  protected static final String KEY_GRADLE_PATH = "newmodule.wrap.gradlePath";
  /**
   * Array of {@link Module} where Jar dependency will be replaced with module dependency.
   */
  protected static final String KEY_MODULES_FOR_DEPENDENCY_UPDATE = "newmodule.wrap.updateDeps";
  /**
   * Boolean value weather the source jar archive will be removed.
   */
  protected static final String KEY_MOVE_ARCHIVE = "newmodule.wrap.move";

  private final Project myProject;
  private Collection<ModuleWizardStep> steps;
  private NewModuleWizardState myWizardState;

  public WrapArchiveWizardPath(@NotNull NewModuleWizardState wizardState,
                               @Nullable Project project,
                               @Nullable final TemplateWizardStep.UpdateListener listener,
                               @Nullable Icon sidePanelIcon) {
    myWizardState = wizardState;
    myProject = project;
    steps = Collections.singleton(new WrapArchiveOptionsStep(project, wizardState, sidePanelIcon));
    if (listener != null) {
      for (ModuleWizardStep step : steps) {
        step.registerStepListener(listener::update);
      }
    }
  }

  @Override
  public Collection<ModuleWizardStep> getSteps() {
    return steps;
  }

  @Override
  public void update() {
    for (ModuleWizardStep step : steps) {
      step.updateStep();
    }
  }

  @Override
  public void createModule() {
    if (myProject != null) {
      File archivePath = new File(myWizardState.getString(KEY_ARCHIVE));
      String path = myWizardState.getString(KEY_GRADLE_PATH);
      boolean move = myWizardState.getBoolean(KEY_MOVE_ARCHIVE);
      Module[] modules = (Module[])myWizardState.get(KEY_MODULES_FOR_DEPENDENCY_UPDATE);
      if (modules == null) {
        modules = new Module[0];
      }
      String gradlePath = makeAbsolute(path);
      GradleSettingsFile settingsFile = GradleSettingsFile.get(myProject);
      CreateModuleFromArchiveAction action =
          new CreateModuleFromArchiveAction(myProject, settingsFile, gradlePath, archivePath, move, modules);
      action.execute();
    }
  }

  /**
   * Prefixes string with colon if there isn't one already there.
   */
  @VisibleForTesting
  @Nullable
  @Contract("null -> null;!null -> !null")
  static String makeAbsolute(String string) {
    if (string == null) {
      return null;
    }
    else if (string.trim().length() == 0) {
      return SdkConstants.GRADLE_PATH_SEPARATOR;
    }
    else if (!string.startsWith(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      return SdkConstants.GRADLE_PATH_SEPARATOR + string.trim();
    }
    else {
      return string.trim();
    }
  }


  @Override
  public boolean isStepVisible(ModuleWizardStep step) {
    return steps.contains(step);
  }

  @Override
  public Collection<String> getExcludedTemplates() {
    return Collections.singleton(NewModuleWizardState.MODULE_IMPORT_NAME);
  }

  @Override
  public Collection<ChooseTemplateStep.MetadataListItem> getBuiltInTemplates() {
    ChooseTemplateStep.MetadataListItem template =
        ImportSourceModulePath.createImportTemplateWithCustomName(NewModuleWizardState.ARCHIVE_IMPORT_NAME,
                                                                  "Imports an existing JAR or AAR package as a new project module");
    return Collections.singleton(template);
  }

  @Override
  public boolean supportsGlobalWizard() {
    return false;
  }
}
