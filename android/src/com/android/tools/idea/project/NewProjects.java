/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.project;

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.TargetSelectionMode;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW;
import static org.jetbrains.android.util.AndroidUtils.addRunConfiguration;

public class NewProjects {
  private static final Logger LOG = Logger.getInstance(NewProjects.class);

  public static void createIdeaProjectDir(@NotNull File projectRootDirPath) throws IOException {
    File ideaDirPath = new File(projectRootDirPath, Project.DIRECTORY_STORE_FOLDER);
    if (ideaDirPath.isDirectory()) {
      // "libraries" is hard-coded in com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
      File librariesDir = new File(ideaDirPath, "libraries");
      if (librariesDir.exists()) {
        // remove contents of libraries. This is useful when importing existing projects that may have invalid library entries (e.g.
        // created with Studio 0.4.3 or earlier.)
        boolean librariesDirDeleted = delete(librariesDir);
        if (!librariesDirDeleted) {
          LOG.info(String.format("Failed to delete %1$s'", librariesDir.getPath()));
        }
      }
    }
    else {
      ensureExists(ideaDirPath);
    }
  }

  @NotNull
  public static Project createProject(@NotNull String projectName, @NotNull String projectPath) throws ConfigurationException {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project newProject = projectManager.createProject(projectName, projectPath);
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new IDEA project");
    }
    return newProject;
  }

  public static void activateProjectView(@NotNull Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(PROJECT_VIEW);
    if (window != null) {
      window.activate(null, false);
    }
  }

  public static void createRunConfigurations(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();
    RunManager runManager = RunManager.getInstance(module.getProject());
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    List<RunConfiguration> configs = runManager.getConfigurationsList(configurationFactory.getType());
    for (RunConfiguration config : configs) {
      if (config instanceof AndroidRunConfiguration) {
        AndroidRunConfiguration androidRunConfig = (AndroidRunConfiguration)config;
        if (androidRunConfig.getConfigurationModule().getModule() == module) {
          // There is already a run configuration for this module.
          return;
        }
      }
    }
    addRunConfiguration(facet, null, false, TargetSelectionMode.SHOW_DIALOG, null);
  }
}
