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

import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.workbench.SideModel.EventType;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class LayeredPanelTest {
  @Mock
  private AttachedToolWindow<String> myToolWindow1;
  @Mock
  private AttachedToolWindow<String> myToolWindow2;
  @Mock
  private SideModel<String> myModel;

  private EventType myEventType;
  private JComponent myMainContent;
  private JComponent myToolWindowComponent1;
  private JComponent myToolWindowComponent2;
  private LayeredPanel<String> myPanel;
  private ThreeComponentsSplitter mySplitter;
  private PropertiesComponent myPropertiesComponent;
  private JComponent myContainer;

  @Before
  public void before() {
    initMocks(this);
    myMainContent = new JLabel();
    myToolWindowComponent1 = new JLabel();
    myToolWindowComponent2 = new JLabel();
    myPropertiesComponent = new PropertiesComponentMock();
    myPanel = new LayeredPanel<>("BENCH", myMainContent, myModel, myPropertiesComponent);
    mySplitter = myPanel.getSplitter();
    myContainer = mySplitter.getInnerComponent();
    myEventType = EventType.LOCAL_UPDATE;
    when(myToolWindow1.getComponent()).thenReturn(myToolWindowComponent1);
    when(myToolWindow1.getDefinition()).thenReturn(PalettePanelToolContent.getDefinition());
    when(myToolWindow2.getComponent()).thenReturn(myToolWindowComponent2);
    when(myToolWindow2.getDefinition()).thenReturn(PalettePanelToolContent.getOtherDefinition());
    when(myToolWindow1.getToolName()).thenReturn("PALETTE");
    when(myToolWindow2.getToolName()).thenReturn("OTHER");
  }

  @After
  public void after() {
    Disposer.dispose(myPanel);
  }

  @Test
  public void testDoLayout() {
    myPanel.setSize(800, 600);
    myPanel.doLayout();
    assertThat(myMainContent.getBounds()).isEqualTo(new Rectangle(0, 0, 800, 600));
    assertThat(mySplitter.getBounds()).isEqualTo(new Rectangle(0, 0, 800, 600));
  }

  @Test
  public void testNoVisibleTools() {
    when(myModel.getHiddenSliders()).thenReturn(ImmutableList.of(myToolWindow1, myToolWindow2));
    myPanel.modelChanged(myModel, myEventType);
    assertThat(mySplitter.isVisible()).isFalse();
    assertThat(myContainer.getComponents()).asList().containsExactly(myToolWindowComponent1, myToolWindowComponent2);
  }

  @Test
  public void testVisibleToolOnLeftSide() {
    when(myModel.getHiddenSliders()).thenReturn(ImmutableList.of(myToolWindow2));
    when(myModel.getVisibleAutoHideTool()).thenReturn(myToolWindow1);
    when(myToolWindow1.isLeft()).thenReturn(true);
    myPanel.modelChanged(myModel, myEventType);
    assertThat(mySplitter.isVisible()).isTrue();
    assertThat(mySplitter.getFirstComponent()).isEqualTo(myToolWindowComponent1);
    assertThat(mySplitter.getLastComponent()).isNull();
    assertThat(myContainer.getComponents()).asList().containsExactly(myToolWindowComponent2);
  }

  @Test
  public void testVisibleToolOnRightSide() {
    when(myModel.getHiddenSliders()).thenReturn(ImmutableList.of(myToolWindow2));
    when(myModel.getVisibleAutoHideTool()).thenReturn(myToolWindow1);
    when(myToolWindow1.isLeft()).thenReturn(false);
    myPanel.modelChanged(myModel, myEventType);
    assertThat(mySplitter.isVisible()).isTrue();
    assertThat(mySplitter.getFirstComponent()).isNull();
    assertThat(mySplitter.getLastComponent()).isEqualTo(myToolWindowComponent1);
    assertThat(myContainer.getComponents()).asList().containsExactly(myToolWindowComponent2);
  }

  @Test
  public void testThatWidthIsMaintained() {
    when(myModel.getHiddenSliders()).thenReturn(ImmutableList.of(myToolWindow2));
    when(myModel.getVisibleAutoHideTool()).thenReturn(myToolWindow1);
    when(myToolWindow1.isLeft()).thenReturn(true);
    myPanel.modelChanged(myModel, myEventType);

    mySplitter.setFirstSize(JBUI.scale(700));
    fireWidthChanged();
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.PALETTE.UNSCALED.WIDTH", -1)).isEqualTo(700);

    myPanel.modelChanged(myModel, myEventType);
    assertThat(mySplitter.getFirstSize()).isEqualTo(JBUI.scale(700));
  }

  private void fireWidthChanged() {
    ComponentEvent event = new ComponentEvent(myContainer, MouseEvent.MOUSE_RELEASED);
    for (ComponentListener listener : myContainer.getComponentListeners()) {
      listener.componentResized(event);
    }
  }
}
