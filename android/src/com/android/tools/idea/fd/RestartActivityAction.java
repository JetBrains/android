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
package com.android.tools.idea.fd;

import com.android.ddmlib.IDevice;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Action which restarts an activity in the running app
 */
public class RestartActivityAction extends AnAction {
  public RestartActivityAction() {
    super("Restart Activity", null, AllIcons.Actions.Reset);
  }

  @Override
  public void update(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    e.getPresentation().setEnabled(module != null && FastDeployManager.isPatchableApp(module));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      return;
    }
    restartActivity(module);
  }

  /** Restarts the activity associated with the given module */
  public static void restartActivity(@NotNull Module module) {
    for (IDevice device : FastDeployManager.findDevices(module.getProject())) {
      if (FastDeployManager.isAppRunning(device, module)) {
        FastDeployManager.restartActivity(device, module);
      }
    }
  }
}
