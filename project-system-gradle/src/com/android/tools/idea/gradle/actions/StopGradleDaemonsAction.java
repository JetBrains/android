/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * Internal action that stops all running Gradle daemons.
 */
public class StopGradleDaemonsAction extends DumbAwareAction {
  private static final String TITLE = "Stop Gradle Daemons and Restart";

  public StopGradleDaemonsAction() {
    super(TITLE);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationManager.getApplication().executeOnPooledThread(GradleProjectSystemUtil::stopAllGradleDaemonsAndRestart);
  }
}
