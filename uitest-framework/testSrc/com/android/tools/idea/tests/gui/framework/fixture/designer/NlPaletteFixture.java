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

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SearchTextFieldFixture;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.palette.PalettePanel;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.intellij.ui.SearchTextField;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JListItemFixture;
import org.fest.swing.query.ComponentShowingQuery;
import org.fest.swing.query.ComponentSizeQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.android.tools.idea.tests.gui.framework.matcher.Matchers.byType;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class NlPaletteFixture extends ComponentFixture<NlPaletteFixture, PalettePanel> {
  private final PalettePanel myNewPalette;
  private SearchTextFieldFixture mySearchField;

  @NotNull
  public static NlPaletteFixture create(@NotNull Robot robot, @NotNull Container root) {
    return new NlPaletteFixture(robot, robot.finder().findByType(root, PalettePanel.class, true));
  }

  private NlPaletteFixture(@NotNull Robot robot, @Nullable PalettePanel palette) {
    super(NlPaletteFixture.class, robot, palette);
    myNewPalette = palette;
  }

  public boolean isShowing() {
    return ComponentShowingQuery.isShowing(target());
  }

  @NotNull
  public JListFixture getCategoryList() {
    return new JListFixture(robot(), myNewPalette.getCategoryList());
  }

  public JListFixture getItemList() {
    return new JListFixture(robot(), myNewPalette.getItemList());
  }

  @Nullable
  public LayoutEditorFileType getLayoutEditorFileType() {
    return execute(new GuiQuery<>() {
      @Override
      protected @Nullable LayoutEditorFileType executeInEDT() {
        return target().getLayoutEditorFileType();
      }
    });
  }

  @Nullable
  public String getPaletteId() {
    LayoutEditorFileType type = getLayoutEditorFileType();
    return type != null ? type.getPaletteId() : null;
  }

  /**
   * When the palette is being created it may not be done with laying itself out.
   * Both the category list and the item list must be showing and have a positive width.
   * The {@link #getItemList(String)} may fail if we do not wait for initialization since
   * it a mouse click is generated based on the size of the category list.
   */
  public void waitForInitialization(int secondsToWait, LayoutEditorFileType expectedType) {
    Wait.seconds(secondsToWait)
      .expecting("Palette is initialized")
      .until(() ->
               isShowing() &&
               expectedType.getPaletteId().equals(getPaletteId()) &&
               ComponentShowingQuery.isShowing(getCategoryList().target()) &&
               ComponentSizeQuery.sizeOf(getCategoryList().target()).width > 0 &&
               ComponentShowingQuery.isShowing(getItemList().target()) &&
               ComponentSizeQuery.sizeOf(getItemList().target()).width > 0
      );
  }

  /**
   * Get the item list of a group in the palette.
   *
   * @param group the name of the group that identifies the item list.
   *              Use an empty string if there is no groupings in this palette (like for menues).
   */
  @NotNull
  public JListFixture getItemList(@NotNull String group) {
    if (!group.isEmpty()) {
      getCategoryList().selectItem(group);
      getCategoryList().requireSelectedItems(group);
    }

    JList itemList = myNewPalette.getItemList();
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

  @NotNull
  public SearchTextFieldFixture getSearchTextField() {
    if (mySearchField == null) {
      SearchTextField searchTextField = waitUntilShowing(robot(), getMyPanel().getParent(), byType(SearchTextField.class));
      mySearchField = new SearchTextFieldFixture(robot(), searchTextField);
    }
    return mySearchField;
  }

  public void clickItem(@NotNull String group, @NotNull String itemValue, int fromRightEdge) {
    JListFixture itemList = getItemList(group);
    JListItemFixture item = itemList.item(itemValue);
    Rectangle bounds = itemList.target().getCellBounds(item.index(), item.index());
    Point pos = new Point(bounds.x + bounds.width - fromRightEdge, bounds.y + bounds.height / 2);
    robot().waitForIdle();
    robot().click(itemList.target(), pos, MouseButton.LEFT_BUTTON, 1);
  }

  @NotNull
  public List<String> getItemTitles(@NotNull JListFixture itemList) {
    //noinspection unchecked
    ListModel<Palette.Item> model = itemList.target().getModel();
    List<String> titles = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      titles.add(model.getElementAt(index).getTitle());
    }
    return titles;
  }

  @NotNull
  private JPanel getMyPanel() {
    return myNewPalette;
  }
}
