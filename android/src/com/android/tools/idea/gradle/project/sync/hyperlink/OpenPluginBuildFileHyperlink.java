/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;

public class OpenPluginBuildFileHyperlink extends NotificationHyperlink {
  public OpenPluginBuildFileHyperlink() {
    super("openPluginBuildFile", "Open File");
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (project.isInitialized()) {
      AndroidPluginInfo result = searchInBuildFilesOnly(project);
      if (result != null && result.getPluginBuildFile() != null) {
        Navigatable openFile = new OpenFileDescriptor(project, result.getPluginBuildFile(), -1, -1, false);
        if (openFile.canNavigate()) {
          openFile.navigate(true);
          return;
        }
      }
    }
    Messages.showErrorDialog(project, "Failed to find plugin version on Gradle files.", "Quick Fix");
  }
}
