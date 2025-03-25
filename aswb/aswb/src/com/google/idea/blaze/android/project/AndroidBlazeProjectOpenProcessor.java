/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.project;

import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.google.idea.blaze.android.projectsystem.BlazeProjectSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.BlazeIcons;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.jdom.JDOMException;

/** Allows directly opening a project with project data directory embedded within the project. */
public class AndroidBlazeProjectOpenProcessor extends ProjectOpenProcessor {
  @Override
  public String getName() {
    return Blaze.defaultBuildSystemName() + " Project";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  private static final String DEPRECATED_PROJECT_DATA_SUBDIRECTORY = ".project";

  @Nullable
  private static VirtualFile getIdeaSubdirectory(VirtualFile file) {
    VirtualFile projectSubdirectory = file.findChild(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY);
    if (projectSubdirectory == null || !projectSubdirectory.isDirectory()) {
      projectSubdirectory = file.findChild(DEPRECATED_PROJECT_DATA_SUBDIRECTORY);
      if (projectSubdirectory == null || !projectSubdirectory.isDirectory()) {
        return null;
      }
    }
    VirtualFile ideaSubdirectory = projectSubdirectory.findChild(Project.DIRECTORY_STORE_FOLDER);
    VirtualFile blazeSubdirectory =
        projectSubdirectory.findChild(BlazeDataStorage.BLAZE_DATA_SUBDIRECTORY);
    return ideaSubdirectory != null
            && ideaSubdirectory.isDirectory()
            && blazeSubdirectory != null
            && blazeSubdirectory.isDirectory()
        ? ideaSubdirectory
        : null;
  }

  @Override
  public boolean canOpenProject(VirtualFile file) {
    return getIdeaSubdirectory(file) != null;
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return true;
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }

  @Nullable
  @Override
  public Project doOpenProject(
      VirtualFile file, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    ProjectManagerEx pm = ProjectManagerEx.getInstanceEx();
    VirtualFile ideaSubdirectory = getIdeaSubdirectory(file);
    if (ideaSubdirectory == null) {
      return null;
    }
    VirtualFile projectSubdirectory = ideaSubdirectory.getParent();
    return pm.openProject(
      projectSubdirectory.toNioPath(),
      ProjectSystemService.Companion.projectSystemOpenProjectTask(BlazeProjectSystemProvider.ID, forceOpenInNewFrame, projectToClose)
    );
  }
}
