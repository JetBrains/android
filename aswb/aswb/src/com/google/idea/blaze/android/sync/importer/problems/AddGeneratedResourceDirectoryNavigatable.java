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
package com.google.idea.blaze.android.sync.importer.problems;

import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.projectview.GenfilesPath;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import java.io.File;

/**
 * Action to include a generated resource directory in the local project view file.
 *
 * <p>This is unfortunately wrapped in a navigatable, because the IntelliJ ProblemsView class
 * doesn't have an extension point or action group ID for extending the right-click actions.
 *
 * <p>Ideally, we would have a quick fix in the java R.type.foo and xml @type/foo references, but to
 * know which generated directory contains type.foo, we would need to parse the resources, which
 * would be expensive.
 */
class AddGeneratedResourceDirectoryNavigatable implements Navigatable {

  private final Project project;
  private final File projectViewFile;
  private final ArtifactLocation generatedResDir;

  AddGeneratedResourceDirectoryNavigatable(
      Project project, File projectViewFile, ArtifactLocation generatedResDir) {
    this.project = project;
    this.projectViewFile = projectViewFile;
    this.generatedResDir = generatedResDir;
  }

  @Override
  public void navigate(boolean requestFocus) {
    int addToProjectView =
        Messages.showYesNoDialog(
            String.format(
                "Include generated resource directory \"%s\" in project view?",
                generatedResDir.getRelativePath()),
            "Include generated resource",
            null);
    if (addToProjectView == Messages.YES) {
      addDirectoryToProjectView(project, projectViewFile, generatedResDir);
    }
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  private static void addDirectoryToProjectView(
      Project project, File projectViewFile, ArtifactLocation generatedResDir) {
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              ListSection<GenfilesPath> existingSection =
                  builder.getLast(GeneratedAndroidResourcesSection.KEY);
              ListSection.Builder<GenfilesPath> directoryBuilder =
                  ListSection.update(GeneratedAndroidResourcesSection.KEY, existingSection);
              directoryBuilder.add(new GenfilesPath(generatedResDir.getRelativePath()));
              builder.replace(existingSection, directoryBuilder);
              return true;
            });
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();
    VirtualFile projectView = VfsUtil.findFileByIoFile(projectViewFile, false);
    if (projectView != null) {
      FileEditorManager.getInstance(project)
          .openEditor(new OpenFileDescriptor(project, projectView), true);
    }
  }
}
