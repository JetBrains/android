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
package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class AbstractClientToggleAction extends ToggleAction {
  private final DeviceContext myDeviceContext;

  public AbstractClientToggleAction(@NotNull DeviceContext context,
                                    @Nullable String text,
                                    @Nullable String description,
                                    @Nullable Icon icon) {
    super(text, description, icon);
    myDeviceContext = context;
  }

  @Override
  public final boolean isSelected(AnActionEvent e) {
    Client c = myDeviceContext.getSelectedClient();
    if (c == null || !c.isValid()) {
      return false;
    }
    return isSelected(c);
  }

  @Override
  public final void setSelected(AnActionEvent e, boolean state) {
    Client c = myDeviceContext.getSelectedClient();
    if (c == null || !c.isValid()) {
      return;
    }
    setSelected(c);
  }

  @Override
  public final void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();

    Client c = myDeviceContext.getSelectedClient();
    if (c == null || !c.isValid()) {
      presentation.setEnabled(false);
      return;
    }

    String text = getActiveText(c);
    presentation.setText(text);
    presentation.setEnabled(true);
  }

  protected abstract boolean isSelected(@NotNull Client c);

  protected abstract void setSelected(@NotNull Client c);

  /**
   * Returns the description of the this action given the client's current
   * state. This will override the text passed into the constructor.
   */
  @NotNull
  protected abstract String getActiveText(@NotNull Client c);
}
