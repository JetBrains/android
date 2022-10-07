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
package com.android.tools.adtui.workbench;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import javax.swing.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class MinimizedPanelTest {
  @Mock
  private AttachedToolWindow<String> myToolWindow1;
  @Mock
  private AttachedToolWindow<String> myToolWindow2;
  @Mock
  private SideModel<String> myModel;

  private AbstractButton myButton1;
  private AbstractButton myButton2;
  private MinimizedPanel<String> myPanel;

  @Before
  public void before() {
    initMocks(this);
    myButton1 = new AbstractButton() {};
    myButton2 = new AbstractButton() {};
    myPanel = new MinimizedPanel<>(Side.LEFT, myModel);
    when(myToolWindow1.getMinimizedButton()).thenReturn(myButton1);
    when(myToolWindow1.getDefinition()).thenReturn(PalettePanelToolContent.getDefinition());
    when(myToolWindow2.getMinimizedButton()).thenReturn(myButton2);
    when(myToolWindow2.getDefinition()).thenReturn(PalettePanelToolContent.getOtherDefinition());
    when(myToolWindow1.getToolName()).thenReturn("PALETTE");
    when(myToolWindow2.getToolName()).thenReturn("OTHER");
  }

  @Test
  public void testEmptyMinimizedPanel() {
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.emptyList());
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.emptyList());
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    assertThat(myPanel.isVisible()).isFalse();
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).isEmpty();
    assertThat(findHiddenComponents()).isEmpty();
  }

  @Test
  public void testAllToolsAreVisible() {
    when(myToolWindow1.isMinimized()).thenReturn(false);
    when(myToolWindow2.isMinimized()).thenReturn(false);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    assertThat(myPanel.isVisible()).isFalse();
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2);
    assertThat(findHiddenComponents()).isEmpty();
  }

  @Test
  public void testAllToolsAreVisibleOneIsAutoHide() {
    when(myToolWindow1.isMinimized()).thenReturn(false);
    when(myToolWindow2.isMinimized()).thenReturn(false);
    when(myToolWindow2.isAutoHide()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    assertThat(myPanel.isVisible()).isTrue();
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2);
    assertThat(findHiddenComponents()).isEmpty();
  }

  @Test
  public void testOneToolVisible() {
    when(myToolWindow1.isMinimized()).thenReturn(false);
    when(myToolWindow2.isMinimized()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    assertThat(myPanel.isVisible()).isTrue();
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2);
    assertThat(findHiddenComponents()).isEmpty();
  }

  @Test
  public void testNoToolsVisible() {
    when(myToolWindow1.isMinimized()).thenReturn(true);
    when(myToolWindow2.isMinimized()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    assertThat(myPanel.isVisible()).isTrue();
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2);
    assertThat(findHiddenComponents()).isEmpty();
  }

  @Test
  public void testDrag() {
    myPanel.setSize(21, 400);
    myButton1.setPreferredSize(new Dimension(21, 21));
    myButton2.setPreferredSize(new Dimension(21, 21));
    when(myToolWindow1.isMinimized()).thenReturn(true);
    when(myToolWindow2.isMinimized()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    myPanel.doLayout();

    myPanel.drag(myToolWindow1, -99999999);
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);
    myPanel.doLayout();

    myPanel.drag(myToolWindow1, 26);
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);

    myPanel.drag(myToolWindow1, 125);
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton1);

    myPanel.drag(myToolWindow1, 135);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton1, myButton2).inOrder();

    myPanel.drag(myToolWindow1, 255);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton1, myButton2).inOrder();

    myPanel.drag(myToolWindow1, 265);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2, myButton1).inOrder();

    myPanel.drag(myToolWindow1, 99999999);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2, myButton1).inOrder();
  }

  @Test
  public void testDragOutOfPanel() {
    myPanel.setSize(21, 400);
    myButton1.setPreferredSize(new Dimension(21, 21));
    myButton2.setPreferredSize(new Dimension(21, 21));
    when(myToolWindow1.isMinimized()).thenReturn(true);
    when(myToolWindow2.isMinimized()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    myPanel.doLayout();

    myPanel.drag(myToolWindow1, 265);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2, myButton1).inOrder();
    assertThat(findHiddenComponents()).isEmpty();

    myPanel.dragExit(myToolWindow1);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2);
    assertThat(findHiddenComponents()).containsExactly(myButton1);
    verify(myModel, never()).changeToolSettingsAfterDragAndDrop(eq(myToolWindow1), any(Side.class), any(Split.class), anyInt());
  }

  @Test
  public void testDragInFromOtherPanel() {
    myPanel.setSize(21, 400);
    myButton1.setPreferredSize(new Dimension(21, 21));
    myButton2.setPreferredSize(new Dimension(21, 21));
    when(myToolWindow1.isMinimized()).thenReturn(true);
    when(myToolWindow2.isMinimized()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.emptyList());
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    myPanel.doLayout();

    myPanel.drag(myToolWindow2, 26);
    assertThat(findVisibleComponents(Split.TOP)).containsExactly(myButton2, myButton1).inOrder();
  }

  @Test
  public void testDragAndDrop() {
    myPanel.setSize(21, 400);
    myButton1.setPreferredSize(new Dimension(21, 21));
    myButton2.setPreferredSize(new Dimension(21, 21));
    when(myToolWindow1.isMinimized()).thenReturn(true);
    when(myToolWindow2.isMinimized()).thenReturn(true);
    when(myModel.getTopTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getBottomTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);
    myPanel.doLayout();

    myPanel.drag(myToolWindow1, 265);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2, myButton1).inOrder();
    assertThat(findHiddenComponents()).isEmpty();

    myPanel.dragDrop(myToolWindow1, 265);
    assertThat(findVisibleComponents(Split.TOP)).isEmpty();
    assertThat(findVisibleComponents(Split.BOTTOM)).containsExactly(myButton2);
    assertThat(findHiddenComponents()).containsExactly(myButton1);
    verify(myModel).changeToolSettingsAfterDragAndDrop(eq(myToolWindow1), eq(Side.LEFT), eq(Split.BOTTOM), eq(1));
  }

  private List<Component> findVisibleComponents(@NotNull Split from) {
    List<Component> visible = new ArrayList<>();
    Split at = Split.TOP;
    for (Component component : myPanel.getComponents()) {
      if (component instanceof Box.Filler) {
        at = Split.BOTTOM;
      }
      else if (at == from && component.isVisible()) {
        visible.add(component);
      }
    }
    return visible;
  }

  private List<Component> findHiddenComponents() {
    List<Component> hidden = new ArrayList<>();
    for (Component component : myPanel.getComponents()) {
      if (!(component instanceof Box.Filler) && !component.isVisible()) {
        hidden.add(component);
      }
    }
    return hidden;
  }
}
