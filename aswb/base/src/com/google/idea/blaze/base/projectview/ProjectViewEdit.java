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
package com.google.idea.blaze.base.projectview;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

/** Represents a modification to one or more project view files. */
public class ProjectViewEdit {

  private static final Logger logger = Logger.getInstance(ProjectViewEdit.class);
  private final Project project;
  private final List<Modification> modifications;

  private ProjectViewEdit(Project project, List<Modification> modifications) {
    this.project = project;
    this.modifications = modifications;
  }

  private static class Modification {
    ProjectView oldProjectView;
    ProjectView newProjectView;
    File projectViewFile;
  }

  @Nullable
  private static ProjectViewSet reloadProjectView(Project project) {
    return Scope.root(
        context -> {
          return ProjectViewManager.getInstance(project).reloadProjectView(context);
        });
  }

  /** Creates a new edit that modifies the local project view only. */
  @Nullable
  public static ProjectViewEdit editLocalProjectView(Project project, ProjectViewEditor editor) {
    List<Modification> modifications = Lists.newArrayList();
    ProjectViewSet oldProjectViewSet = reloadProjectView(project);
    if (oldProjectViewSet == null) {
      return null;
    }

    ProjectViewSet.ProjectViewFile projectViewFile = oldProjectViewSet.getTopLevelProjectViewFile();
    if (projectViewFile == null) {
      return null;
    }

    ProjectView.Builder builder = ProjectView.builder(projectViewFile.projectView);
    if (editor.editProjectView(builder)) {
      Modification modification = new Modification();
      modification.newProjectView = builder.build();
      modification.oldProjectView = projectViewFile.projectView;
      modification.projectViewFile = projectViewFile.projectViewFile;
      modifications.add(modification);
    }
    return new ProjectViewEdit(project, modifications);
  }

  public void apply() {
    apply(true);
  }

  public void undo() {
    apply(false);
  }

  private void apply(boolean isApply) {
    SaveUtil.saveAllFiles();
    for (Modification modification : modifications) {
      ProjectView projectView = isApply ? modification.newProjectView : modification.oldProjectView;
      String projectViewText = ProjectViewParser.projectViewToString(projectView);
      try {
        ProjectViewStorageManager.getInstance()
            .writeProjectView(projectViewText, modification.projectViewFile);
      } catch (IOException e) {
        logger.error(e);
        Messages.showErrorDialog(
            project,
            "Could not write updated project view. Is the file write protected?",
            "Edit Failed");
      }
    }
    // now that we've changed it, reload our in-memory view
    reloadProjectView(project);
  }

  public boolean hasModifications() {
    return !modifications.isEmpty();
  }

  /** Interface for an edit to the project view */
  public interface ProjectViewEditor {
    boolean editProjectView(ProjectView.Builder builder);
  }
}
