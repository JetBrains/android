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
package com.android.tools.idea.gradle.adtimport;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.ImportUtil.IMPORT_SUMMARY_TXT;
import static com.android.tools.idea.util.ToolWindows.activateProjectView;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_IMPORT_ADT_MODULE;

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.uipreview.EditorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Importer which can import an ADT project as a Gradle project (it will first
 * run the Eclipse importer, which generates a Gradle project, and then it will
 * delegate to {@link org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder}
 * to perform the IntelliJ model import.
 */
public class AdtImportBuilder extends ProjectImportBuilder<String> {
  private File mySelectedProject;
  private GradleImport myImporter;
  private final boolean myCreateProject;

  public AdtImportBuilder(boolean createProject) {
    myCreateProject = createProject;
  }

  @NotNull
  @Override
  public String getName() {
    return "ADT (Eclipse Android)";
  }

  public void setSelectedProject(@NotNull File selectedProject) {
    mySelectedProject = selectedProject;
    List<File> projects = Collections.singletonList(mySelectedProject);
    myImporter = createImporter(projects);
  }

  @NotNull
  private GradleImport createImporter(@NotNull List<File> projects) {
    GradleImport importer = new GradleImport();
    importer.setImportIntoExisting(!myCreateProject);

    if (myCreateProject) {
      importer.setCreateGradleWrapper(true);
      AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
      if (sdkData != null) {
        importer.setSdkLocation(sdkData.getLocationFile());
      }
    }
    importer.importProjects(projects);
    return importer;
  }

  @Nullable
  public GradleImport getImporter() {
    return myImporter;
  }

  @Override
  public Icon getIcon() {
    // TODO: Can we get the ADT bundle icon?
    return AllIcons.Providers.Eclipse;
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
  public void setList(List<String> list) {
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  @Nullable
  @Override
  public List<Module> commit(Project project,
                             @Nullable ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             @Nullable ModifiableArtifactModel artifactModel) {
    File destDir = getBaseDirPath(project);
    try {
      boolean isExportSuccessful = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> {
          if (!destDir.exists()) {
            boolean ok = destDir.mkdirs();
            if (!ok) {
              throw new IOException("Could not create destination directory");
            }
          }
          // Re-read the project here since one of the wizard steps can have modified the importer options,
          // and that affects the imported state (for example, if you enable/disable the replace-lib-with-dependency
          // options, the set of modules can change)
          readProjects();
          if (!myImporter.getErrors().isEmpty()) {
            return false;
          }

          myImporter.exportProject(destDir, true);
          VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
          if (projectDir != null) {
            projectDir.refresh(false, true);
          }
          return true;
        },
        "Migrating project...", false, null
      );
      if (!isExportSuccessful) {
        return null;
      }
    }
    catch (IOException e) {
      Logger.getInstance(AdtImportBuilder.class).error(e);
      return null;
    }

    try {
      GradleSyncListener syncListener = new GradleSyncListener() {
        @Override
        public void syncSucceeded(@NotNull Project project) {
          ApplicationManager.getApplication().invokeLater(() -> {
            activateProjectView(project);
            openSummary(project);
          });
        }

        @Override
        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
          ApplicationManager.getApplication().invokeLater(() -> openSummary(project));
        }
      };
      GradleProjectImporter importer = GradleProjectImporter.getInstance();
      if (myCreateProject) {
        GradleProjectImporter.Request request = new GradleProjectImporter.Request(project);
        request.isNewProject = true;
        importer.importProjectNoSync(request);
        Path projectDir = getBaseDirPath(request.project).getAbsoluteFile().toPath();
        OpenProjectTask options = OpenProjectTask.build().withProject(project).withForceOpenInNewFrame(true);
        ProjectManagerEx.getInstanceEx().openProject(projectDir, options);
      }
      else {
        GradleSyncInvoker.getInstance().requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_IMPORT_ADT_MODULE), syncListener);
      }
    }
    catch (Throwable e) {
      Messages.showErrorDialog(project, e.getMessage(), "ADT Project Import");
    }

    return Collections.emptyList();
  }

  public void readProjects() {
    myImporter.importProjects(Collections.singletonList(mySelectedProject));
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public boolean validate(@Nullable Project current, Project dest) {
    return super.validate(current, dest);
  }

  private static void openSummary(Project project) {
    VirtualFile summary = project.getBaseDir().findChild(IMPORT_SUMMARY_TXT);
    if (summary != null) {
      EditorUtil.openEditor(project, summary);
    }
  }

  @Nullable
  public static AdtImportBuilder getBuilder(@Nullable WizardContext context) {
    if (context != null) {
      return (AdtImportBuilder)context.getProjectBuilder();
    }

    return null;
  }

  @Override
  @Nullable
  public Project createProject(String name, String path) {
    return GradleProjectImporter.getInstance().createProject(name, new File(path));
  }
}
