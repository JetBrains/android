/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link ToolWindowDefinition} of a Palette.
 */
public class PaletteDefinition extends ToolWindowDefinition<DesignSurface<?>> {

  public PaletteDefinition(@NotNull Project project, @NotNull Side side, @NotNull Split split, @NotNull AutoHide autoHide) {
    super("Palette ", AllIcons.Toolwindows.ToolWindowPalette, "PALETTE", side, split, autoHide,
          (disposable) -> createPalettePanel(project, disposable));
  }

  private static ToolContent<DesignSurface<?>> createPalettePanel(@NotNull Project project, @NotNull Disposable parentDisposable) {
    if (project.isDisposed()) {
      return null;
    }
    return new PalettePanel(project, parentDisposable);
  }
}
