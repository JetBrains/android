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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.designer.palette.PaletteGroup;
import com.intellij.designer.palette.PaletteItem;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture representing the component palette in an associated layout editor
 */
public class LayoutPaletteFixture {
  private final Robot myRobot;
  private final LayoutEditorFixture myEditorFixture;
  private final AndroidDesignerEditorPanel myPanel;

  public LayoutPaletteFixture(@NotNull Robot robot,
                              @NotNull LayoutEditorFixture editorFixture,
                              @NotNull AndroidDesignerEditorPanel panel) {
    myRobot = robot;
    myEditorFixture = editorFixture;
    myPanel = panel;
  }

  /**
   * Looks up and returns a palette entry of the given title if found
   */
  @Nullable
  public LayoutPaletteComponentFixture findByName(@NotNull String title) {
    for (PaletteGroup group : myPanel.getPaletteGroups()) {
      for (PaletteItem item : group.getItems()) {
        if (title.equals(item.getTitle())) {
          return new LayoutPaletteComponentFixture(myRobot, myEditorFixture, myPanel, group, item);
        }
      }
    }

    return null;
  }
}
