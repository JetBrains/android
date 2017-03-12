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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.GENERATED_SOURCES;
import static com.android.tools.idea.project.messages.MessageType.INFO;
import static com.android.tools.idea.project.messages.MessageType.WARNING;

class ExtraGeneratedFolderValidationStrategy extends AndroidProjectValidationStrategy {
  @NotNull private final List<File> myExtraGeneratedSourceFolderPaths = new ArrayList<>();

  ExtraGeneratedFolderValidationStrategy(@NotNull Project project) {
    super(project);
  }

  @Override
  void validate(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
    File[] sourceFolderPaths = androidModel.getExtraGeneratedSourceFolderPaths();
    Collections.addAll(myExtraGeneratedSourceFolderPaths, sourceFolderPaths);
  }

  @Override
  void fixAndReportFoundIssues() {
    if (!myExtraGeneratedSourceFolderPaths.isEmpty()) {
      GradleSyncMessages messages = GradleSyncMessages.getInstance(getProject());
      Collections.sort(myExtraGeneratedSourceFolderPaths);

      // Warn users that there are generated source folders at the wrong location.
      for (File folder : myExtraGeneratedSourceFolderPaths) {
        // Have to add a word before the path, otherwise IDEA won't show it.
        String[] text = {"Folder " + folder.getPath()};
        messages.report(new SyncMessage(GENERATED_SOURCES, WARNING, text));
      }

      messages.report(new SyncMessage(GENERATED_SOURCES, INFO, "3rd-party Gradle plug-ins may be the cause"));
    }
  }

  @VisibleForTesting
  @NotNull
  List<File> getExtraGeneratedSourceFolderPaths() {
    return myExtraGeneratedSourceFolderPaths;
  }
}
