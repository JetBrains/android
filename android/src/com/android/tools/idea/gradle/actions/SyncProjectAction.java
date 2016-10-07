/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Re-imports (syncs) an Android-Gradle project, without showing the "Import Project" wizard.
 */
public class SyncProjectAction extends AndroidStudioGradleAction {
  public SyncProjectAction() {
    this("Sync Project with Gradle Files");
  }

  protected SyncProjectAction(@NotNull String text) {
    super(text);
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    BuildVariantView.getInstance(project).projectImportStarted();
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    try {
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
    finally {
      presentation.setEnabled(true);
    }
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    boolean enabled = !GradleSyncState.getInstance(project).isSyncInProgress();
    e.getPresentation().setEnabled(enabled);
  }
}
