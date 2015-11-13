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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Action which performs an instant run, without restarting
 */
public class InstantRunWithoutRestart extends AnAction {
  public InstantRunWithoutRestart() {
    this("Perform Instant Run", AndroidIcons.RunIcons.Replay);
  }

  protected InstantRunWithoutRestart(String title, @NotNull Icon icon) {
    super(title, null, icon);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      return;
    }
    perform(module);
  }

  private void perform(Module module) {
    Project project = module.getProject();
    if (!FastDeployManager.isInstantRunEnabled(project) || !FastDeployManager.isPatchableApp(module)) {
      return;
    }
    List<IDevice> devices = FastDeployManager.findDevices(project);
    FastDeployManager manager = FastDeployManager.get(project);
    for (IDevice device : devices) {
      if (FastDeployManager.isAppRunning(device, module)) {
        if (FastDeployManager.buildIdsMatch(device, module)) {
          manager.performUpdate(device, getUpdateMode(), module);
        } else {
          FastDeployManager.postBalloon(MessageType.ERROR,
                                        "Local Gradle build id doesn't match what's installed on the device; full build required",
                                        project);
        }
        break;
      }
    }
  }

  @NotNull
  protected UpdateMode getUpdateMode() {
    return UpdateMode.HOT_SWAP;
  }
}
