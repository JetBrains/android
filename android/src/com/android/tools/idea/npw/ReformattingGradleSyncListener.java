/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

final class ReformattingGradleSyncListener extends NewProjectImportGradleSyncListener {
  @NotNull
  private final Collection<File> myTargetFiles;

  @NotNull
  private final Collection<File> myFilesToOpen;

  ReformattingGradleSyncListener(@NotNull Collection<File> targetFiles, @NotNull Collection<File> filesToOpen) {
    myTargetFiles = targetFiles;
    myFilesToOpen = filesToOpen;
  }

  @Override
  public void syncSucceeded(@NotNull final Project project) {
    StartupManagerEx manager = StartupManagerEx.getInstanceEx(project);

    if (manager.postStartupActivityPassed()) {
      reformatRearrangeAndOpen(project);
    }
    else {
      manager.registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          reformatRearrangeAndOpen(project);
        }
      });
    }
  }

  private void reformatRearrangeAndOpen(@NotNull Project project) {
    TemplateUtils.reformatAndRearrange(project, myTargetFiles);
    TemplateUtils.openEditors(project, myFilesToOpen, true);
  }
}
