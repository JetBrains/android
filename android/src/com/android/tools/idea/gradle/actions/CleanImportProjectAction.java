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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.intellij.ide.RecentProjectsManagerImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Closes, removes all IDEA-related files (.idea folder and .iml files) and imports a project.
 */
public class CleanImportProjectAction extends DumbAwareAction {
  private static final String MESSAGE_FORMAT = "This action will:\n" +
                                               "1. Close project '%1$s'\n" +
                                               "2. Delete all project files (.idea folder and .iml files)\n" +
                                               "3. Import the project\n\n" +
                                               "You will lose custom project-wide settings. Are you sure you want to continue?";

  private static final String TITLE = "Close, Clean and Re-Import Project";

  private static final Logger LOG = Logger.getInstance(CleanImportProjectAction.class);

  public CleanImportProjectAction() {
    super(TITLE);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && isGradleProject(project)) {
      String projectName = project.getName();
      int answer = Messages.showYesNoDialog(project, String.format(MESSAGE_FORMAT, projectName), TITLE, null);
      if (answer == Messages.YES) {
        LOG.info(String.format("Closing, cleaning and re-importing project '%1$s'...", projectName));
        List<File> filesToDelete = collectFilesToDelete(project);
        File projectDir = new File(project.getBasePath());
        close(project);
        delete(filesToDelete, projectName);
        try {
          LOG.info(String.format("About to import project '%1$s'.", projectName));
          GradleProjectImporter.getInstance().importNewlyCreatedProject(projectName, projectDir, null, null, null);
          LOG.info(String.format("Done importing project '%1$s'.", projectName));
        }
        catch (Exception error) {
          String title = getErrorMessageTitle(error);
          Messages.showErrorDialog(error.getMessage(), title);
          LOG.info(String.format("Failed to import project '%1$s'.", projectName));
        }
      }
    }
  }
  @NotNull
  private static List<File> collectFilesToDelete(@NotNull Project project) {
    VirtualFile projectFile = project.getProjectFile();
    if (projectFile == null) {
      // This is the default project. This will NEVER happen.
      return Collections.emptyList();
    }
    List<File> filesToDelete = Lists.newArrayList();
    filesToDelete.add(VfsUtilCore.virtualToIoFile(projectFile.getParent()));
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      VirtualFile moduleFile = module.getModuleFile();
      if (moduleFile != null) {
        filesToDelete.add(VfsUtilCore.virtualToIoFile(moduleFile));
      }
    }
    return filesToDelete;
  }

  private static void close(@NotNull Project project) {
    String projectName = project.getName();
    ProjectUtil.closeAndDispose(project);
    RecentProjectsManagerImpl.getInstance().updateLastProjectPath();
    WelcomeFrame.showIfNoProjectOpened();
    LOG.info(String.format("Closed project '%1$s'.", projectName));
  }

  private static void delete(@NotNull final List<File> files, @NotNull String projectName) {
    Project project = ProjectManager.getInstance().getDefaultProject();
    String title = String.format("Cleaning up project '%1$s", projectName);
    ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setFraction(0d);
        int fileCount = files.size();
        for (int i = 0; i < fileCount; i++) {
          File file = files.get(i);
          String path = file.getPath();
          LOG.info(String.format("About to delete file '%1$s'", path));
          if (!FileUtil.delete(file)) {
            LOG.info(String.format("Failed to delete file '%1$s'", path));
          }
          indicator.setFraction(i / fileCount);
        }
        indicator.setFraction(1d);
      }
    });
  }

  @NotNull
  private static String getErrorMessageTitle(@NotNull Exception e) {
    if (e instanceof ConfigurationException) {
      return ((ConfigurationException)e).getTitle();
    }
    return TITLE;
  }

  @Override
  public void update(AnActionEvent e) {
    boolean isGradleProject = isGradleProject(e.getProject());
    Presentation presentation = e.getPresentation();
    presentation.setVisible(isGradleProject);
    presentation.setEnabled(isGradleProject);
  }

  private static boolean isGradleProject(@Nullable Project project) {
    return project != null && Projects.isGradleProject(project);
  }
}
