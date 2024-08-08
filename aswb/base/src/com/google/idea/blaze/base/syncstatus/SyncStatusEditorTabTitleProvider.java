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
package com.google.idea.blaze.base.syncstatus;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

/** Changes the tab title for unsynced files. */
public class SyncStatusEditorTabTitleProvider implements EditorTabTitleProvider, DumbAware {
  @Nullable
  @Override
  public String getEditorTabTitle(Project project, VirtualFile file) {
    if (Blaze.getProjectType(project).equals(BlazeImportSettings.ProjectType.UNKNOWN)) {
      return null;
    }

    SyncStatus status = SyncStatusContributor.getSyncStatus(project, file);
    if (status == null) {
      return null;
    }

    if (status.equals(SyncStatus.UNSYNCED)) {
      return file.getPresentableName() + " (unsynced)";
    }
    if (status.equals(SyncStatus.IN_PROGRESS)) {
      return file.getPresentableName() + " (syncing...)";
    }
    return null;
  }
}
