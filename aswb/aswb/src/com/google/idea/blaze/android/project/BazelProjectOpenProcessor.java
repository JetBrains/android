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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.BlazeIcons;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Allows directly opening a project (`File` -> `Open folder` in UI).
 */
public class BazelProjectOpenProcessor extends ProjectOpenProcessor {

  public static final String BAZELPROJECT = ".bazelproject";
  public static final String BLAZEPROJECT = ".blazeproject";

  @Override
  public String getName() {
    return Blaze.defaultBuildSystemName() + " Project";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  /**
   * Check if the project directory contains a blaze project file.
   */
  @Override
  public boolean canOpenProject(VirtualFile file) {
    if (file.isDirectory()) {
      return Arrays.stream(file.getChildren()).anyMatch(this::checkIfProjectFile);
    }
    return checkIfProjectFile(file);
  }

  private boolean checkIfProjectFile(VirtualFile file) {
    return file.getPath().contains(BLAZEPROJECT) || file.getPath().contains(BAZELPROJECT);
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return true;
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return true;
  }

  @Nullable
  @Override
  public Project doOpenProject(
      VirtualFile file, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    if (checkIfProjectFile(file)) {
      file = file.getParent();
    }
    ProjectManagerEx pm = ProjectManagerEx.getInstanceEx();
    return pm.openProject(
        file.toNioPath(),
        ProjectSystemService.Companion.projectSystemOpenProjectTask(BlazeProjectSystemProvider.ID,
            forceOpenInNewFrame, projectToClose)
    );
  }
}
