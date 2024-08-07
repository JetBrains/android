/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.project.ExtendableBazelProjectCreator;
import com.google.idea.sdkcompat.general.BaseSdkCompat;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/** A class that knows how to create IDE projects described by a {@link ProjectBuilder}. */
@VisibleForTesting
public class BlazeProjectCreator {
  private static final Logger logger = Logger.getInstance(BlazeProjectCreator.class);

  private final ProjectBuilder projectBuilder;

  /**
   * A descriptor and reference to a created IDE project together with some metadata that knows how
   * to open the project in the IDE.
   */
  @VisibleForTesting
  public static class CreatedProjectDescriptor {
    public final Path ideaProjectPath;
    public final Project project;

    public CreatedProjectDescriptor(Path ideaProjectPath, Project project) {
      this.ideaProjectPath = ideaProjectPath;
      this.project = project;
    }

    @VisibleForTesting
    public void openProject() {
      ProjectManagerEx.getInstanceEx()
          .openProject(ideaProjectPath, BaseSdkCompat.createOpenProjectTask(project));

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        SaveAndSyncHandler.getInstance().scheduleProjectSave(project);
      }
    }
  }

  public BlazeProjectCreator(ProjectBuilder projectBuilder) {
    this.projectBuilder = projectBuilder;
  }

  public void doCreate(
      String projectFilePath, String projectName, StorageScheme projectStorageFormat)
      throws IOException {

    CreatedProjectDescriptor createdProjectDescriptor =
        createProject(projectFilePath, projectName, projectStorageFormat);
    if (createdProjectDescriptor == null) {
      return;
    }

    createdProjectDescriptor.openProject();
  }

  @Nullable
  @VisibleForTesting
  public BlazeProjectCreator.CreatedProjectDescriptor createProject(
      String projectFilePath, String projectName, StorageScheme projectStorageFormat)
      throws IOException {
    File projectDir = new File(projectFilePath).getParentFile();
    logger.assertTrue(
        projectDir != null,
        "Cannot create project in '" + projectFilePath + "': no parent file exists");
    FileUtil.ensureExists(projectDir);
    if (projectStorageFormat == StorageScheme.DIRECTORY_BASED) {
      final File ideaDir = new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER);
      FileUtil.ensureExists(ideaDir);
    }

    Optional<Project> returnedValue =
        ExtendableBazelProjectCreator.getInstance()
            .createProject(projectBuilder, projectName, projectFilePath);
    if (returnedValue.isEmpty()) {
      return null;
    }
    Project newProject = returnedValue.get();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    if (!projectBuilder.validate(null, newProject)) {
      return null;
    }

    projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);

    class MyStartup implements Runnable, DumbAware {
      @Override
      public void run() {
        // ensure the dialog is shown after all startup activities are done
        SwingUtilities.invokeLater(
            () -> {
              if (newProject.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
                return;
              }
              ApplicationManager.getApplication()
                  .invokeLater(
                      () -> {
                        if (newProject.isDisposed()) {
                          return;
                        }
                        final ToolWindow toolWindow =
                            ToolWindowManager.getInstance(newProject)
                                .getToolWindow(ToolWindowId.PROJECT_VIEW);
                        if (toolWindow != null) {
                          toolWindow.activate(null);
                        }
                      },
                      ModalityState.NON_MODAL);
            });
      }
    }

    //noinspection deprecation
    StartupManager.getInstance(newProject).registerPostStartupActivity(new MyStartup());

    Path path = Paths.get(projectFilePath);
    ProjectUtil.updateLastProjectLocation(path);

    return new CreatedProjectDescriptor(path, newProject);
  }
}
