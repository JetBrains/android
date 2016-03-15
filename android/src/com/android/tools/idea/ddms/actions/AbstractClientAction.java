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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AbstractClientAction extends AnAction {
  @NotNull protected final DeviceContext myDeviceContext;

  public AbstractClientAction(@NotNull DeviceContext deviceContext,
                              @Nullable String text,
                              @Nullable String description,
                              @Nullable Icon icon) {
    super(text, description, icon);

    myDeviceContext = deviceContext;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  protected final boolean isEnabled() {
    Client c = myDeviceContext.getSelectedClient();
    return (c != null && c.isValid() && canPerformAction());
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    if (isEnabled()) {
      performAction(myDeviceContext.getSelectedClient());
    }
  }

  protected boolean canPerformAction() { return true; }
  protected abstract void performAction(@NotNull Client c);
}
