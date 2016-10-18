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

import com.android.tools.idea.uibuilder.structure.NlComponentTree;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.swing.*;
import java.awt.event.InputEvent;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class NlPaletteAndComponentTreePanelTest extends AndroidTestCase {
  private ActionManager myOldActionManager;
  private ActionManager myActionManager;
  private NlPalettePanel myPalettePanel;
  private NlComponentTree myComponentTree;
  private NlPaletteAndComponentTreePanel myPanel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myActionManager = mock(ActionManager.class);
    myOldActionManager = registerApplicationComponent(ActionManager.class, myActionManager);
    NlPalettePanel realPalettePanel = new NlPalettePanel(getProject(), null);
    myPalettePanel = spy(realPalettePanel);
    myComponentTree = spy(new NlComponentTree(null));
    myPanel = new NlPaletteAndComponentTreePanel(myPalettePanel, myComponentTree);
    Disposer.dispose(doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        Disposer.dispose(realPalettePanel);
        return null;
      }
    }).when(myPalettePanel));

    ActionPopupMenu menu = mock(ActionPopupMenu.class);
    when(myActionManager.createActionPopupMenu(anyString(), any(ActionGroup.class))).thenReturn(menu);
    when(menu.getComponent()).thenReturn(mock(JPopupMenu.class));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      registerApplicationComponent(ActionManager.class, myOldActionManager);
      myPanel.dispose();
    }
    finally {
      super.tearDown();
    }
  }

  public void testRequestFocusInPalette() {
    myPanel.requestFocusInPalette();
    verify(myPalettePanel).requestFocus();
  }

  public void testRequestFocusInComponentTree() {
    myPanel.requestFocusInComponentTree();
    verify(myComponentTree).requestFocus();
  }

  public void testIconAndNameAction() {
    ActionGroup group = activateButton();

    AnActionEvent event = createMockEvent();
    assertThat(group.getChildren(event).length).isEqualTo(3);
    AnAction action = group.getChildren(event)[0];
    action.actionPerformed(event);
    verify(myPalettePanel).setMode(PaletteMode.ICON_AND_NAME);
  }

  public void testLargeIcons() {
    ActionGroup group = activateButton();

    AnActionEvent event = createMockEvent();
    assertThat(group.getChildren(event).length).isEqualTo(3);
    AnAction action = group.getChildren(event)[1];
    action.actionPerformed(event);
    verify(myPalettePanel).setMode(PaletteMode.LARGE_ICONS);
  }

  public void testSmallIcons() {
    ActionGroup group = activateButton();

    AnActionEvent event = createMockEvent();
    assertThat(group.getChildren(event).length).isEqualTo(3);
    AnAction action = group.getChildren(event)[2];
    action.actionPerformed(event);
    verify(myPalettePanel).setMode(PaletteMode.SMALL_ICONS);
  }

  @NotNull
  private static AnActionEvent createMockEvent() {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getPresentation()).thenReturn(new Presentation());
    return event;
  }

  @NotNull
  private ActionGroup activateButton() {
    assertThat(myPanel.getActions().length).isEqualTo(1);
    AnAction action = myPanel.getActions()[0];
    AnActionEvent event = mock(AnActionEvent.class);
    InputEvent input = mock(InputEvent.class);
    when(event.getInputEvent()).thenReturn(input);
    when(input.getComponent()).thenReturn(myPanel);
    action.actionPerformed(event);
    ArgumentCaptor<ActionGroup> captor = ArgumentCaptor.forClass(ActionGroup.class);
    verify(myActionManager).createActionPopupMenu(anyString(), captor.capture());
    return captor.getValue();
  }
}
