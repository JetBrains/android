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
package com.google.idea.blaze.base.syncstatus;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * Implemented on a per-language basis to indicate source files of that language which weren't built
 * in the most recent sync.
 */
public interface SyncStatusContributor {

  ExtensionPointName<SyncStatusContributor> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncStatusContributor");

  /**
   * Returns a {@link SyncStatus} for the given file, or null if it's not relevant (not in the
   * project or otherwise can't be synced).
   */
  @Nullable
  static SyncStatus getSyncStatus(Project project, VirtualFile vf) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      // TODO(b/260643753) update this for querysync
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    return getSyncStatus(project, projectData, vf);
  }

  /**
   * Returns a {@link SyncStatus} for the given file, or null if it's not relevant (not in the
   * project or otherwise can't be synced).
   */
  @Nullable
  static SyncStatus getSyncStatus(Project project, BlazeProjectData projectData, VirtualFile vf) {
    if (!vf.isValid() || !vf.isInLocalFileSystem()) {
      return null;
    }
    boolean handledType =
        Arrays.stream(EP_NAME.getExtensions()).anyMatch(c -> c.handlesFile(projectData, vf));
    if (!handledType) {
      return null;
    }
    if (ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(vf) == null) {
      return null;
    }
    return ProjectTargetManager.getInstance(project).getSyncStatus(new File(vf.getPath()));
  }

  /**
   * Converts a {@link ProjectViewNode} to a corresponding {@link PsiFile}, or returns null if this
   * contributor doesn't handle the given node type for this project. Only to be used with
   * query-sync.
   */
  @Nullable
  default PsiFileAndName toPsiFileAndName(ProjectViewNode<?> node) {
    return null;
  }

  @Nullable
  PsiFileAndName toPsiFileAndName(BlazeProjectData projectData, ProjectViewNode<?> node);

  /** Whether this {@link SyncStatusContributor} handles the given file type, for this project. */
  boolean handlesFile(BlazeProjectData projectData, VirtualFile file);

  /** The {@link PsiFile} and UI text associated with a {@link ProjectViewNode}. */
  class PsiFileAndName {
    final PsiFile psiFile;
    final String name;

    public PsiFileAndName(PsiFile psiFile, String name) {
      this.psiFile = psiFile;
      this.name = name;
    }
  }
}
