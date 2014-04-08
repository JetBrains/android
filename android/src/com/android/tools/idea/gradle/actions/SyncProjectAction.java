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
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/**
 * Re-imports (syncs) an Android-Gradle project, without showing the "Import Project" wizard.
 */
public class SyncProjectAction extends AnAction {
  public SyncProjectAction() {
    super("Sync Project with Gradle Files");
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
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
  }

  @Override
  public void update(AnActionEvent e) {
    if (!AndroidStudioSpecificInitializer.isAndroidStudio()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    boolean enabled = false;
    Project project = e.getProject();
    if (project != null) {
      enabled = !GradleSyncState.getInstance(project).isSyncInProgress();
    }
    e.getPresentation().setEnabled(enabled);
  }
}
