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

import java.awt.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Fixture for an item in the palette
 */
public class LayoutPaletteComponentFixture {
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final Robot myRobot;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final LayoutEditorFixture myEditorFixture;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final AndroidDesignerEditorPanel myPanel;
  private final PaletteGroup myGroup;
  private final PaletteItem myItem;

  LayoutPaletteComponentFixture(@NotNull Robot robot,
                                @NotNull LayoutEditorFixture editorFixture,
                                @NotNull AndroidDesignerEditorPanel panel,
                                @NotNull PaletteGroup group,
                                @NotNull PaletteItem item) {
    myRobot = robot;
    myEditorFixture = editorFixture;
    myPanel = panel;
    myGroup = group;
    myItem = item;
  }

  /**
   * Requires the palette title to be the given title
   */
  public LayoutPaletteComponentFixture requireTitle(@NotNull String title)  {
    assertEquals(title, myItem.getTitle());
    return this;
  }

  /**
   * Requires the palette title to be the given title
   */
  public LayoutPaletteComponentFixture requireCategory(@NotNull String category)  {
    assertEquals(category, myGroup.getName());
    return this;
  }

  /**
   * Requires the palette title to have the given tag
   */
  public LayoutPaletteComponentFixture requireTag(@NotNull String tag)  {
    assertEquals(tag, myItem.getMetaModel().getTag());
    return this;
  }

  /** Clicks the given palette item (which makes it the current palette item) */
  public void click() {
    // Selects item
    fail("Not yet implemented");
  }

  /**
   * Drags this palette item into the layout editor canvas at the given location
   */
  public void dragToEditor(@NotNull Point editorLocation) {
    fail("Not yet implemented");
  }

  /**
   * Drags this palette item into the structure/outline view at the given location
   */
  public void dragToStructureView(@NotNull Point editorLocation) {
    fail("Not yet implemented");
  }
}
