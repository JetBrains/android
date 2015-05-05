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
package com.android.tools.idea.uibuilder.palette;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

public class TogglePaletteModeAction extends ToggleAction {
  private final NlPalettePanel myPalette;
  private final NlPalettePanel.Mode myMode;

  public TogglePaletteModeAction(@NotNull NlPalettePanel palette, @NotNull NlPalettePanel.Mode mode) {
    super(mode.getMenuText());
    myPalette = palette;
    myMode = mode;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myMode == myPalette.getMode();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean unused) {
    myPalette.setMode(myMode);
  }
}
