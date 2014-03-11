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
package com.android.tools.idea.gradle.eclipse;

import com.android.sdklib.SdkManager;
import com.android.tools.gradle.eclipse.GradleImport;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import icons.EclipseIcons;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.service.settings.ImportFromGradleControl;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Importer which can import an ADT project as a Gradle project (it will first
 * run the Eclipse importer, which generates a Gradle project, and then it will
 * delegate to {@link org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder}
 * to perform the IntelliJ model import.
 * */
public class AdtImportBuilder extends ProjectImportBuilder<String> {
  private final GradleProjectImportBuilder myGradleBuilder;
  private File mySelectedProject;
  private GradleImport myImporter;

  public AdtImportBuilder(@NotNull ProjectDataManager dataManager) {
    myGradleBuilder = new GradleProjectImportBuilder(dataManager);
  }

  @NotNull
  @Override
  public String getName() {
    return "ADT (Eclipse Android)";
  }

  public void setSelectedProject(@NotNull File selectedProject) throws IOException {
    mySelectedProject = selectedProject;
    List<File> projects = Arrays.asList(mySelectedProject);
    myImporter = null;
    myImporter = createImporter(projects);
  }

  protected GradleImport createImporter(@NotNull List<File> projects) throws IOException {
    GradleImport importer = new GradleImport();
    importer.importProjects(projects);
    File templates = TemplateManager.getTemplateRootFolder();
    if (templates != null) {
      File wrapper = TemplateManager.getWrapperLocation(templates);
      if (wrapper.exists()) {
        importer.setGradleWrapperLocation(wrapper);
        SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkManager != null) {
          importer.setSdkManager(sdkManager);
        }
      }
    }
    return importer;
  }

  @Nullable
  GradleImport getImporter() {
    return myImporter;
  }

  @Override
  public Icon getIcon() {
    // TODO: Can we get the ADT bundle icon?
    return EclipseIcons.Eclipse;
  }

  @Override
  @Nullable
  public List<String> getList() {
    return null;
  }

  @Override
  public boolean isMarked(String element) {
    return false;
  }

  @Override
  public void setList(List<String> list) throws ConfigurationException {
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @Nullable
  @Override
  public List<Module> commit(final Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {
    File destDir = new File(project.getBasePath());
    try {
      if (!destDir.exists()) {
        boolean ok = destDir.mkdirs();
        if (!ok) {
          throw new IOException("Could not create destination directory");
        }
      }
      // Re-read the project here since one of the wizard steps can have modified the importer options,
      // and that affects the imported state (for example, if you enable/disable the replace-lib-with-dependency
      // options, the set of modules can change)
      myImporter.importProjects(Arrays.asList(mySelectedProject));
      myImporter.exportProject(destDir, true);
    }
    catch (IOException e) {
      Logger.getInstance(AdtImportBuilder.class).error(e);
    }

    ImportFromGradleControl control = myGradleBuilder.getControl(null);
    GradleProjectSettings settings = control.getProjectSettings();
    settings.setExternalProjectPath(destDir.getPath());
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);

    myGradleBuilder.setFileToImport(new File(destDir, GradleConstants.DEFAULT_SCRIPT_NAME).getPath());
    myGradleBuilder.setOpenProjectSettingsAfter(isOpenProjectSettingsAfter());
    myGradleBuilder.setUpdate(isUpdate());
    List<Module> modules = myGradleBuilder.commit(project, model, modulesProvider, artifactModel);

    StartupManagerEx manager = StartupManagerEx.getInstanceEx(project);
    if (!manager.postStartupActivityPassed()) {
      manager.registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          openSummary(project);
        }
      });
    } else {
      openSummary(project);
    }

    return modules;
  }

  private static void openSummary(Project project) {
    VirtualFile summary = project.getBaseDir().findChild(GradleImport.IMPORT_SUMMARY_TXT);
    if (summary != null) {
      TemplateUtils.openEditor(project, summary);
    }
  }
}
