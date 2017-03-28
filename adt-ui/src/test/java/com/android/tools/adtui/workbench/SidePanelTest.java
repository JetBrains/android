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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.Splitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class SidePanelTest {
  @Mock
  private AttachedToolWindow<String> myToolWindow1;
  @Mock
  private AttachedToolWindow<String> myToolWindow2;
  @Mock
  private SideModel<String> myModel;

  private JComponent myToolWindowComponent1;
  private JComponent myToolWindowComponent2;
  private SidePanel<String> myPanel;

  @Before
  public void before() {
    initMocks(this);
    myToolWindowComponent1 = new JLabel();
    myToolWindowComponent2 = new JLabel();
    myPanel = new SidePanel<>(Side.LEFT, myModel);
    when(myToolWindow1.getComponent()).thenReturn(myToolWindowComponent1);
    when(myToolWindow2.getComponent()).thenReturn(myToolWindowComponent2);
    when(myToolWindow1.getToolName()).thenReturn("PALETTE");
    when(myToolWindow2.getToolName()).thenReturn("OTHER");
  }

  @Test
  public void testNoVisibleTools() {
    when(myModel.getVisibleTools(Side.LEFT)).thenReturn(Collections.emptyList());
    when(myModel.getHiddenTools(Side.LEFT)).thenReturn(ImmutableList.of(myToolWindow1, myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);

    assertThat(myPanel.isVisible()).isFalse();
    assertThat(findHiddenComponents()).containsAllOf(myToolWindowComponent1, myToolWindowComponent2);
  }

  @Test
  public void testOneVisibleTool() {
    when(myModel.getVisibleTools(Side.LEFT)).thenReturn(Collections.singletonList(myToolWindow1));
    when(myModel.getHiddenTools(Side.LEFT)).thenReturn(ImmutableList.of(myToolWindow2));
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);

    assertThat(myPanel.isVisible()).isTrue();
    assertThat(findVisibleComponent()).isSameAs(myToolWindowComponent1);
    assertThat(findHiddenComponents()).contains(myToolWindowComponent2);
  }

  @Test
  public void testTwoVisibleTools() {
    when(myModel.getVisibleTools(Side.LEFT)).thenReturn(ImmutableList.of(myToolWindow1, myToolWindow2));
    when(myModel.getHiddenTools(Side.LEFT)).thenReturn(Collections.emptyList());
    myPanel.modelChanged(myModel, SideModel.EventType.LOCAL_UPDATE);

    assertThat(myPanel.isVisible()).isTrue();
    Component visible = findVisibleComponent();
    Splitter splitter = visible instanceof Splitter ? (Splitter)visible : null;
    assertThat(splitter).isNotNull();
    assertThat(splitter.getFirstComponent()).isEqualTo(myToolWindowComponent1);
    assertThat(splitter.getSecondComponent()).isEqualTo(myToolWindowComponent2);
    assertThat(findHiddenComponents()).containsNoneOf(myToolWindowComponent1, myToolWindowComponent2);
  }

  @Nullable
  private Component findVisibleComponent() {
    assert myPanel.getComponentCount() == 1 : "Expecting exactly 1 card view";
    JPanel cardView = (JPanel)myPanel.getComponent(0);
    for (Component component : cardView.getComponents()) {
      if (component.isVisible()) {
        return component;
      }
    }
    return null;
  }

  @NotNull
  private List<Component> findHiddenComponents() {
    assert myPanel.getComponentCount() == 1 : "Expecting exactly 1 card view";
    List<Component> hidden = new ArrayList<>();
    JPanel cardView = (JPanel)myPanel.getComponent(0);
    for (Component component : cardView.getComponents()) {
      if (!component.isVisible()) {
        hidden.add(component);
      }
    }
    return hidden;
  }
}
