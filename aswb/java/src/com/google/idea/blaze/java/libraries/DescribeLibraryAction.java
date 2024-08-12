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
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;

/**
 * Provides a description of a blaze library's source.
 *
 * <p>Currently just the artifact location of the jars.
 */
class DescribeLibraryAction extends BlazeProjectAction {

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
    showLibraryDescription(project, blazeLibrary);
  }

  private static void showLibraryDescription(Project project, BlazeJarLibrary library) {
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                Messages.showInfoMessage(
                    project,
                    library.libraryArtifact.jarForIntellijLibrary().getRelativePath(),
                    "Original path to library jar"));
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean enabled = LibraryActionHelper.findLibraryForAction(e) != null;
    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }
}
