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

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST;

/**
 * Re-imports (syncs) an Android-Gradle project, without showing the "Import Project" wizard.
 */
public class SyncProjectAction extends AndroidStudioGradleAction {
  @NotNull private final GradleSyncInvoker mySyncInvoker;

  public SyncProjectAction() {
    this("Sync Project with Gradle Files");
  }

  protected SyncProjectAction(@NotNull String text) {
    this(text, GradleSyncInvoker.getInstance());
  }

  @VisibleForTesting
  SyncProjectAction(@NotNull String text, @NotNull GradleSyncInvoker syncInvoker) {
    super(text);
    mySyncInvoker = syncInvoker;
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    BuildVariantView.getInstance(project).projectImportStarted();
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    try {
      mySyncInvoker.requestProjectSyncAndSourceGeneration(project, TRIGGER_USER_REQUEST, null);
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
