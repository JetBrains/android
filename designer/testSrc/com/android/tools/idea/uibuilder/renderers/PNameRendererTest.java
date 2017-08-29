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
package com.android.tools.idea.uibuilder.renderers;

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.adtui.ptable.StarState;
import com.android.tools.adtui.ptable.simple.SimpleGroupItem;
import com.android.tools.adtui.ptable.simple.SimpleItem;
import com.android.tools.idea.uibuilder.property.renderer.NlTableNameRenderer;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.CenteredIcon;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.Field;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class PNameRendererTest extends AndroidTestCase {
  private NlTableNameRenderer myRenderer;
  private SimpleItem myTextItem;
  private SimpleItem myStyleItem;
  private SimpleItem myBackgroundItem;
  private SimpleGroupItem myGroupItem;
  private SimpleItem myItem1;
  private SimpleItem myItem2;
  private PTable myTable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRenderer = new NlTableNameRenderer();
    myTextItem = new SimpleItem(ATTR_TEXT, "Hello", StarState.STAR_ABLE);
    myStyleItem = new SimpleItem(ATTR_STYLE, null, StarState.STARRED);
    myBackgroundItem = new SimpleItem(ATTR_BACKGROUND, null, StarState.NOT_STAR_ABLE);
    myItem1 = new SimpleItem(ATTR_TEXT, "World");
    myItem2 = new SimpleItem(ATTR_ELEVATION, "2dp");
    myGroupItem = new SimpleGroupItem("MyGroup", ImmutableList.of(myItem1, myItem2));
    myTable = new PTable(new PTableModel());
    myTable.getModel().setItems(ImmutableList.of(myTextItem, myStyleItem, myBackgroundItem, myGroupItem));
  }

  public void testTextItem() {
    SimpleColoredComponent renderer = render(myTextItem, false, false, 0);
    assertThat(renderer.getIcon()).isNull();
    assertThat(renderer.toString()).isEqualTo("text");
  }

  public void testGroupItem() throws Exception {
    SimpleColoredComponent renderer = render(myGroupItem, false, false, 3);
    assertSameIcon(renderer.getIcon(), UIUtil.getTreeNodeIcon(false, false, false));
    assertThat(renderer.toString()).isEqualTo("MyGroup");
  }

  public void testSubGroupItem() {
    myGroupItem.setExpanded(true);
    SimpleColoredComponent renderer = render(myItem1, false, false, 4);
    assertThat(renderer.getIcon()).isNull();
    assertThat(renderer.toString()).isEqualTo("MyGroup.text");

    renderer = render(myItem2, false, false, 5);
    assertThat(renderer.getIcon()).isNull();
    assertThat(renderer.toString()).isEqualTo("MyGroup.elevation");
  }

  public void testStarState() {
    assertThat(renderStarIcon(myTextItem, false, false, 0)).isNull();
    assertThat(renderStarIcon(myStyleItem, false, false, 1)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES);
    assertThat(renderStarIcon(myBackgroundItem, false, false, 2)).isNull();
  }

  public void testSelectedStarState() {
    assertThat(renderStarIcon(myTextItem, true, false, 0)).isNull();
    assertThat(renderStarIcon(myStyleItem, true, false, 1)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES_SELECTED);
    assertThat(renderStarIcon(myBackgroundItem, true, false, 2)).isNull();
  }

  public void testHoveringStarState() {
    assertThat(renderStarIconWhileHovering(myTextItem, false, false, 0)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES_HOVER);
    assertThat(renderStarIconWhileHovering(myStyleItem, false, false, 1)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES);
    assertThat(renderStarIconWhileHovering(myBackgroundItem, false, false, 2)).isNull();
  }

  public void testHoveringOverSelectedStarState() {
    assertThat(renderStarIconWhileHovering(myTextItem, true, false, 0)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES_HOVER);
    assertThat(renderStarIconWhileHovering(myStyleItem, true, false, 1)).isEqualTo(StudioIcons.LayoutEditor.Properties.FAVORITES_SELECTED);
    assertThat(renderStarIconWhileHovering(myBackgroundItem, true, false, 2)).isNull();
  }

  @NotNull
  private SimpleColoredComponent render(@NotNull PTableItem item, boolean isSelected, boolean cellHasFocus, int row) {
    JPanel panel = (JPanel)myRenderer.getTableCellRendererComponent(myTable, item, isSelected, cellHasFocus, row, 0);
    return (SimpleColoredComponent)panel.getComponent(1);
  }

  @Nullable
  private Icon renderStarIcon(@NotNull PTableItem item, boolean isSelected, boolean cellHasFocus, int row) {
    JPanel panel = (JPanel)myRenderer.getTableCellRendererComponent(myTable, item, isSelected, cellHasFocus, row, 0);
    return ((JLabel)panel.getComponent(0)).getIcon();
  }

  @Nullable
  private Icon renderStarIconWhileHovering(@NotNull PTableItem item, boolean isSelected, boolean cellHasFocus, int row) {
    fireMouseMoved(row);
    JPanel panel = (JPanel)myRenderer.getTableCellRendererComponent(myTable, item, isSelected, cellHasFocus, row, 0);
    return ((JLabel)panel.getComponent(0)).getIcon();
  }

  private static void assertSameIcon(@NotNull Icon icon, @NotNull Icon expected) throws Exception {
    while (icon instanceof CenteredIcon && expected instanceof CenteredIcon) {
      CenteredIcon centeredIcon = (CenteredIcon)icon;
      CenteredIcon expectedIcon = (CenteredIcon)expected;
      assertThat(centeredIcon.getIconHeight()).isEqualTo(expectedIcon.getIconHeight());
      assertThat(centeredIcon.getIconWidth()).isEqualTo(expectedIcon.getIconWidth());
      icon = getIcon(centeredIcon);
      expected = getIcon(expectedIcon);
    }
    assertThat(icon).isEqualTo(expected);
  }

  private static Icon getIcon(@NotNull CenteredIcon icon) throws Exception {
    Field myIconField = icon.getClass().getDeclaredField("myIcon");
    myIconField.setAccessible(true);
    return (Icon)myIconField.get(icon);
  }

  private void fireMouseMoved(int row) {
    Rectangle rect = myTable.getCellRect(row, 0, false);
    int x = rect.x + 5;
    int y = rect.y + rect.height / 2;
    MouseEvent event = new MouseEvent(myTable, 0, 0, 0, x, y, 1, false);
    for (MouseMotionListener listener : myTable.getMouseMotionListeners()) {
      listener.mouseMoved(event);
    }
  }
}
