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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.StepListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Wraps existing AAR or Jar files into projects.
 */
public class WrapArchiveWizardPath implements WizardPath {
  protected static final String KEY_ARCHIVE = "newmodule.wrap.archivePath";
  protected static final String KEY_GRADLE_PATH = "newmodule.wrap.gradlePath";
  private final Project myProject;
  private Collection<ModuleWizardStep> steps;
  private NewModuleWizardState myWizardState;

  public WrapArchiveWizardPath(@NotNull NewModuleWizardState wizardState,
                               @Nullable Project project,
                               @Nullable final TemplateWizardStep.UpdateListener listener,
                               @NotNull Disposable disposable) {
    myWizardState = wizardState;
    myProject = project;
    steps = Collections.<ModuleWizardStep>singleton(new WrapArchiveOptionsStep(project, wizardState, disposable));
    if (listener != null) {
      for (ModuleWizardStep step : steps) {
        step.registerStepListener(new StepListener() {
          @Override
          public void stateChanged() {
            listener.update();
          }
        });
      }
    }
  }

  @VisibleForTesting
  protected static String getBuildGradleText(File jarName) {
    return String.format("configurations.create(\"default\")\n" + "artifacts.add(\"default\", file('%1$s'))", jarName.getName());
  }

  @Override
  public Collection<ModuleWizardStep> getSteps() {
    return steps;
  }

  @Override
  public void update() {
    if (isActive()) {
      for (ModuleWizardStep step : steps) {
        step.updateStep();
      }
    }
  }

  private boolean isActive() {
    return myWizardState.myMode == NewModuleWizardState.Mode.WRAP_ARCHIVE;
  }

  @Override
  public void createModule() {
    if (isActive() && myProject != null) {
      final File archivePath = new File(myWizardState.getString(KEY_ARCHIVE));
      String path = myWizardState.getString(KEY_GRADLE_PATH);
      final String gradlePath = GradleUtil.makeAbsolute(path);
      assert gradlePath != null;
      GradleSettingsFile settingsFile = GradleSettingsFile.get(myProject);
      new CreateModuleFromArchiveAction(myProject, settingsFile, gradlePath, archivePath).execute();
    }
  }

  @Override
  public boolean isStepVisible(ModuleWizardStep step) {
    return isActive() && steps.contains(step);
  }

  @Override
  public Collection<String> getExcludedTemplates() {
    return Collections.singleton(NewModuleWizardState.MODULE_IMPORT_NAME);
  }

  @Override
  public Collection<ChooseTemplateStep.MetadataListItem> getBuiltInTemplates() {
    ChooseTemplateStep.MetadataListItem template =
      ImportSourceModulePath.createImportTemplateWithCustomName(NewModuleWizardState.ARCHIVE_IMPORT_NAME);
    return Collections.singleton(template);
  }

  private static class CreateModuleFromArchiveAction extends WriteCommandAction<Object> {
    private final Project myProject;
    private final String myGradlePath;
    private final File myArchivePath;

    public CreateModuleFromArchiveAction(@NotNull Project project,
                                         @Nullable GradleSettingsFile settingsFile,
                                         @NotNull String gradlePath,
                                         @NotNull File archivePath) {
      super(project, String.format("Create module %1$s", gradlePath),
            settingsFile != null ? settingsFile.getPsiFile() : null);
      myProject = project;
      myGradlePath = gradlePath;
      myArchivePath = archivePath;
    }

    @Override
    protected void run(@NotNull Result<Object> result) throws Throwable {
      File subprojectLocation = GradleUtil.getDefaultSubprojectLocation(myProject.getBaseDir(), myGradlePath);
      try {
        VirtualFile moduleRoot = VfsUtil.createDirectoryIfMissing(subprojectLocation.getAbsolutePath());
        VirtualFile sourceFile = VfsUtil.findFileByIoFile(myArchivePath, true);
        if (sourceFile != null && moduleRoot != null) {
          VfsUtil.copy(this, sourceFile, moduleRoot);
          VirtualFile buildGradle = moduleRoot.createChildData(this, SdkConstants.FN_BUILD_GRADLE);
          VfsUtil.saveText(buildGradle, getBuildGradleText(myArchivePath));
          GradleSettingsFile settingsGradle = GradleSettingsFile.getOrCreate(myProject);
          settingsGradle.addModule(myGradlePath, VfsUtilCore.virtualToIoFile(moduleRoot));
        }
      }
      catch (IOException e) {
        Logger.getInstance(WrapArchiveWizardPath.class).error(e);
      }
    }

    @Override
    protected boolean isGlobalUndoAction() {
      return true;
    }

    @Override
    protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
      return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
    }
  }
}
