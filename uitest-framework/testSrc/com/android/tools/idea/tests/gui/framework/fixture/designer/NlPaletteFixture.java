/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.designer;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.palette.NlPalettePanel;
import com.android.tools.idea.uibuilder.palette2.PalettePanel;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NlPaletteFixture extends ComponentFixture<NlPaletteFixture, Component> {
  private final PalettePanel myNewPalette;
  private final NlPalettePanel myOldPalette;

  @NotNull
  public static NlPaletteFixture create(@NotNull Robot robot, @NotNull Container root) {
    if (StudioFlags.NELE_NEW_PALETTE.get()) {
      return new NlPaletteFixture(robot, robot.finder().findByType(root, PalettePanel.class, true));
    }
    else {
      return new NlPaletteFixture(robot, robot.finder().findByType(root, NlPalettePanel.class, true));
    }
  }

  private NlPaletteFixture(@NotNull Robot robot, @Nullable PalettePanel palette) {
    super(NlPaletteFixture.class, robot, palette);
    myNewPalette = palette;
    myOldPalette = null;
  }

  private NlPaletteFixture(@NotNull Robot robot, @Nullable NlPalettePanel palette) {
    super(NlPaletteFixture.class, robot, palette);
    myNewPalette = null;
    myOldPalette = palette;
  }

  @NotNull
  private JListFixture getCategoryList() {
    return new JListFixture(robot(), myNewPalette != null ? myNewPalette.getCategoryList() : myOldPalette.getTreeGrid().getCategoryList());
  }

  /**
   * Get the item list of a group in the palette.
   *
   * @param group the name of the group that identifies the item list.
   *              Use an empty string if there is no groupings in this palette (like for menues).
   */
  @NotNull
  private JListFixture getItemList(@NotNull String group) {
    if (!group.isEmpty()) {
      getCategoryList().selectItem(group);
    }

    JList itemList;
    if (myNewPalette != null) {
      itemList = myNewPalette.getItemList();
    }
    else {
      // Wait until the list has been expanded in UI (eliminating flakiness).
      itemList = GuiTests.waitUntilShowing(robot(), myOldPalette.getTreeGrid(), Matchers.byName(JList.class, group));
    }
    Wait.seconds(1).expecting("the items to be populated").until(() -> itemList.getModel().getSize() > 0);
    return new JListFixture(robot(), itemList);
  }

  /**
   * Start dragging a component from the palette.
   *
   * @param group the name of the group where the item is found. Use an empty string if there is no groupings (like in menues).
   * @param item  the name of the item to be dragged
   */
  public void dragComponent(@NotNull String group, @NotNull String item) {
    getItemList(group).drag(item);
  }
}
