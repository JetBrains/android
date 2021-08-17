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

package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class TerminateVMAction extends AbstractClientAction {
  public TerminateVMAction(DeviceContext context) {
    super(context,
          AndroidBundle.message("android.ddms.actions.terminate.vm"),
          AndroidBundle.message("android.ddms.actions.terminate.vm.description"),
          AllIcons.Actions.Suspend);
  }

  @Override
  protected void performAction(@NotNull Client c) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (!c.isValid()) {
        return;
      }

      try {
        // Kill the app in case it's in the crashed state.
        // Note that ClientData#getPackageName doesn't necessarily have the real package name, so hopefully:
        // 1) This won't kill the wrong process if a global process rename happens to overlap with another app.
        // 2) We don't have a global process rename, since we don't know its package name here (prior to R).
        c.getDevice().kill(c.getClientData().getPackageName());
        c.kill();
      }
      catch (Throwable t) {
        Logger.getInstance(TerminateVMAction.class).warn(t);
      }
    });
  }
}
