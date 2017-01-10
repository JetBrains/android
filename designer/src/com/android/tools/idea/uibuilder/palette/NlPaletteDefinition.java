/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_FONT;
import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

public class NlPaletteDefinition extends ToolWindowDefinition<NlDesignSurface> {

  public NlPaletteDefinition(@NotNull Project project, @NotNull Side side, @NotNull Split split, @NotNull AutoHide autoHide) {
    super("Palette ", AllIcons.Toolwindows.ToolWindowPalette, "PALETTE", side, split, autoHide, getInitialWidth(),
          DEFAULT_MINIMUM_BUTTON_SIZE, () -> createPalettePanel(project));
  }

  private static ToolContent<NlDesignSurface> createPalettePanel(@NotNull Project project) {
    if (project.isDisposed()) {
      return null;
    }
    if (!Boolean.getBoolean("use.old.palette")) {
      // TODO: Remove the code for the old palette
      return new NlPalettePanel(project, null);
    }
    else {
      return new NlOldPalettePanel(project, null);
    }
  }

  private static int getInitialWidth() {
    JComponent component = new JPanel();
    return Math.max(ToolWindowDefinition.DEFAULT_SIDE_WIDTH,
                    component.getFontMetrics(DEFAULT_FONT).stringWidth("AppCompat ConstraintLayout") + 40);
  }
}
