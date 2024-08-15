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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.projectview.ExcludeLibrarySection;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;

class ExcludeLibraryAction extends BlazeProjectAction {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.HIDDEN;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    Library library = LibraryActionHelper.findLibraryForAction(e);
    if (library == null) {
      return;
    }
    BlazeJarLibrary blazeLibrary =
        LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
    if (blazeLibrary == null) {
      Messages.showErrorDialog(
          project, "Could not find this library in the project.", CommonBundle.getErrorTitle());
      return;
    }

    final LibraryArtifact libraryArtifact = blazeLibrary.libraryArtifact;
    final String path = libraryArtifact.jarForIntellijLibrary().getRelativePath();

    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              ListSection<Glob> existingSection = builder.getLast(ExcludeLibrarySection.KEY);
              builder.replace(
                  existingSection,
                  ListSection.update(ExcludeLibrarySection.KEY, existingSection)
                      .add(new Glob(path)));
              return true;
            });
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();

    BlazeSyncManager.getInstance(project)
        .incrementalProjectSync(/* reason= */ "ExcludeLibraryAction");
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean enabled = LibraryActionHelper.findLibraryForAction(e) != null;
    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }
}
