/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * An action group containing Android tools (AVD Manager, SDK Manager, etc.). With IDEA-246051 in mind, we want to show Android tools menu
 * whenever Android SDK is configured in IDEA regardless of Android Facet presence. This behavior will enable
 * IDE users developing cross-platform applications (e.g. with react native) using android tools, e.g. AVD Manager.
 */
public class AndroidToolsActionGroup extends DefaultActionGroup implements DumbAware {

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setVisible(project != null && !project.isDisposed() && shouldBeVisible(project));
  }

  @VisibleForTesting
  boolean shouldBeVisible(@NotNull Project project) {
    return CommonAndroidUtil.getInstance().isAndroidProject(project) || IdeSdks.getInstance().hasConfiguredAndroidSdk();
  }
}
