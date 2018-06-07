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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

public class InstallArtifactHyperlink extends NotificationHyperlink {
  @NotNull private final String myPath;

  public InstallArtifactHyperlink(@NotNull String path) {
    super("install.m2.artifact", "Install artifact and sync project");
    myPath = path;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<String> requested = new ArrayList<>();
    requested.add(myPath);
    ModelWizardDialog dialog = createDialogForPaths(project, requested);
    if (dialog != null) {
      dialog.setTitle("Install Missing Components");
      if (dialog.showAndGet()) {
        GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
      }
    }
  }
}
