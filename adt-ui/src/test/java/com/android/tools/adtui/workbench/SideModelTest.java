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

import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Properties;

import static com.android.tools.adtui.workbench.SideModel.EventType.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class SideModelTest {
  private static final String PROPERTY_PREFIX = "PROP";
  private static final String CONTEXT = "CONTEXT";

  private Properties myProperties;
  @Mock
  private AttachedToolWindow<String> myToolWindow1;
  @Mock
  private AttachedToolWindow<String> myToolWindow2;
  @Mock
  private AttachedToolWindow<String> myToolWindow3;
  @Mock
  private AttachedToolWindow<String> myToolWindow4;
  @Mock
  private AttachedToolWindow<String> myToolWindow5;
  @Mock
  private AttachedToolWindow<String> myFloatingToolWindow1;
  @Mock
  private AttachedToolWindow<String> myFloatingToolWindow2;
  @Mock
  private SideModel.Listener<String> myListener;
  @Mock
  private Project myProject;
  @InjectMocks
  private SideModel<String> mySideModel;

  @Before
  public void setUp() {
    initMocks(this);
    myProperties = new Properties();
    initProperties(myToolWindow1, myToolWindow2, myToolWindow3, myToolWindow4, myToolWindow5, myFloatingToolWindow1, myFloatingToolWindow2);
    myToolWindow1.setLeft(true);
    myToolWindow2.setLeft(true);
    myToolWindow2.setSplit(true);
    myToolWindow3.setMinimized(true);
    myToolWindow3.setLeft(true);
    myToolWindow3.setSplit(true);
    myFloatingToolWindow1.setFloating(true);
    myFloatingToolWindow1.setLeft(true);
    myFloatingToolWindow2.setFloating(true);

    mySideModel.setContext(CONTEXT);
    mySideModel.addListener(myListener);
    mySideModel.add(myToolWindow1);
    mySideModel.add(myToolWindow2);
    mySideModel.add(myToolWindow3);
    mySideModel.add(myToolWindow4);
    mySideModel.add(myToolWindow5);
    mySideModel.add(myFloatingToolWindow1);
    mySideModel.add(myFloatingToolWindow2);
  }

  @Test
  public void testGetProject() {
    assertThat(mySideModel.getProject()).isSameAs(myProject);
  }

  @Test
  public void testGetContext() {
    assertThat(mySideModel.getContext()).isSameAs(CONTEXT);
  }

  @Test
  public void testDefaultLayout() {
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow2).inOrder();
    assertThat(mySideModel.getVisibleTools(Side.RIGHT)).containsExactly(myToolWindow4);
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getHiddenTools(Side.RIGHT)).containsExactly(myToolWindow5);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1);
    assertThat(mySideModel.getTopTools(Side.RIGHT)).containsExactly(myToolWindow4, myToolWindow5).inOrder();
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow2, myToolWindow3).inOrder();
    assertThat(mySideModel.getBottomTools(Side.RIGHT)).isEmpty();
    assertThat(mySideModel.getVisibleAutoHideTool()).isNull();
    assertThat(mySideModel.getHiddenSliders()).isEmpty();
    assertThat(mySideModel.getFloatingTools()).containsExactly(myFloatingToolWindow1, myFloatingToolWindow2).inOrder();
  }

  @Test
  public void testMoveWindowToLeftReplacesLeftVisibleWindow() {
    myToolWindow4.setLeft(true);
    mySideModel.update(myToolWindow4, PropertyType.LEFT);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow4, myToolWindow2).inOrder();
    assertThat(mySideModel.getVisibleTools(Side.RIGHT)).isEmpty();
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow3);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow4).inOrder();
    assertThat(mySideModel.getTopTools(Side.RIGHT)).containsExactly(myToolWindow5);
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testMoveWindowToRightAtEmptySpot() {
    myToolWindow2.setLeft(false);
    mySideModel.update(myToolWindow2, PropertyType.LEFT);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow1);
    assertThat(mySideModel.getVisibleTools(Side.RIGHT)).containsExactly(myToolWindow4, myToolWindow2).inOrder();
    assertThat(mySideModel.getTopTools(Side.RIGHT)).containsExactly(myToolWindow4, myToolWindow5).inOrder();
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getBottomTools(Side.RIGHT)).containsExactly(myToolWindow2);
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testMinimizeOneLeftHandToolWindow() {
    myToolWindow1.setMinimized(true);
    mySideModel.update(myToolWindow1, PropertyType.MINIMIZED);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow2);
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow3).inOrder();
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1);
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testRestoreMinimizedToolWindow() {
    myToolWindow3.setMinimized(false);
    mySideModel.update(myToolWindow3, PropertyType.MINIMIZED);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow3).inOrder();
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow2);
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow2, myToolWindow3).inOrder();
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testRestoreFloatingWindow() {
    myFloatingToolWindow1.setFloating(false);
    mySideModel.update(myFloatingToolWindow1, PropertyType.FLOATING);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myFloatingToolWindow1, myToolWindow2).inOrder();
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow3).inOrder();
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1, myFloatingToolWindow1).inOrder();
    assertThat(mySideModel.getFloatingTools()).containsExactly(myFloatingToolWindow2);
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE_FLOATING_WINDOW));
  }

  @Test
  public void testAutoHideClosed() {
    myToolWindow1.setAutoHide(true);
    myToolWindow1.setMinimized(true);
    mySideModel.update(myToolWindow1, PropertyType.AUTO_HIDE);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow2);
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1);
    assertThat(mySideModel.getVisibleAutoHideTool()).isNull();
    assertThat(mySideModel.getHiddenSliders()).containsExactly(myToolWindow1);
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testAutoHideOpen() {
    myToolWindow1.setAutoHide(true);
    myToolWindow1.setMinimized(false);
    mySideModel.update(myToolWindow1, PropertyType.AUTO_HIDE);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow2);
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1);
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow2, myToolWindow3).inOrder();
    assertThat(mySideModel.getVisibleAutoHideTool()).isSameAs(myToolWindow1);
    assertThat(mySideModel.getHiddenSliders()).isEmpty();
    verify(myListener).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testAutoHideClosesPreviousAutoHide() {
    myToolWindow1.setAutoHide(true);
    myToolWindow1.setMinimized(false);
    mySideModel.update(myToolWindow1, PropertyType.AUTO_HIDE);
    myToolWindow2.setAutoHide(true);
    myToolWindow2.setMinimized(true);
    mySideModel.update(myToolWindow2, PropertyType.AUTO_HIDE);

    myToolWindow2.setMinimized(false);
    mySideModel.update(myToolWindow2, PropertyType.MINIMIZED);

    assertThat(mySideModel.getVisibleTools(Side.LEFT)).isEmpty();
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow2).inOrder();
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getVisibleAutoHideTool()).isSameAs(myToolWindow2);
    assertThat(mySideModel.getHiddenSliders()).containsExactly(myToolWindow1);
    verify(myListener, times(3)).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testSetAutoHideAndBack() {
    myToolWindow1.setAutoHide(true);
    myToolWindow1.setMinimized(false);
    mySideModel.update(myToolWindow1, PropertyType.AUTO_HIDE);

    myToolWindow1.setAutoHide(false);
    mySideModel.update(myToolWindow1, PropertyType.AUTO_HIDE);
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow2).inOrder();
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow3).inOrder();
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1).inOrder();
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow2, myToolWindow3);
    assertThat(mySideModel.getVisibleAutoHideTool()).isNull();
    assertThat(mySideModel.getHiddenSliders()).isEmpty();
    verify(myListener, times(2)).modelChanged(eq(mySideModel), eq(UPDATE));
  }

  @Test
  public void testUpdateAll() {
    myToolWindow2.setMinimized(true);
    myToolWindow3.setMinimized(false);
    myToolWindow4.setLeft(true);
    myToolWindow4.setMinimized(true);
    myToolWindow5.setAutoHide(true);
    myFloatingToolWindow2.setFloating(false);
    myFloatingToolWindow2.setMinimized(true);
    mySideModel.updateLocally();

    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow3).inOrder();
    assertThat(mySideModel.getVisibleTools(Side.RIGHT)).isEmpty();
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow2, myToolWindow4).inOrder();
    assertThat(mySideModel.getHiddenTools(Side.RIGHT)).containsExactly(myFloatingToolWindow2);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow1, myToolWindow4).inOrder();
    assertThat(mySideModel.getTopTools(Side.RIGHT)).containsExactly(myToolWindow5, myFloatingToolWindow2).inOrder();
    assertThat(mySideModel.getBottomTools(Side.LEFT)).containsExactly(myToolWindow2, myToolWindow3).inOrder();
    assertThat(mySideModel.getBottomTools(Side.RIGHT)).isEmpty();
    assertThat(mySideModel.getVisibleAutoHideTool()).isNull();
    assertThat(mySideModel.getHiddenSliders()).containsExactly(myToolWindow5);
    assertThat(mySideModel.getFloatingTools()).containsExactly(myFloatingToolWindow1);
    verify(myListener).modelChanged(eq(mySideModel), eq(LOCAL_UPDATE));
  }

  @Test
  public void testSwap() {
    mySideModel.swap();

    assertThat(mySideModel.getVisibleTools(Side.RIGHT)).containsExactly(myToolWindow1, myToolWindow2).inOrder();
    assertThat(mySideModel.getVisibleTools(Side.LEFT)).containsExactly(myToolWindow4);
    assertThat(mySideModel.getHiddenTools(Side.RIGHT)).containsExactly(myToolWindow3);
    assertThat(mySideModel.getHiddenTools(Side.LEFT)).containsExactly(myToolWindow5);
    assertThat(mySideModel.getTopTools(Side.RIGHT)).containsExactly(myToolWindow1);
    assertThat(mySideModel.getTopTools(Side.LEFT)).containsExactly(myToolWindow4, myToolWindow5).inOrder();
    assertThat(mySideModel.getBottomTools(Side.RIGHT)).containsExactly(myToolWindow2, myToolWindow3).inOrder();
    assertThat(mySideModel.getBottomTools(Side.LEFT)).isEmpty();
    assertThat(mySideModel.getVisibleAutoHideTool()).isNull();
    assertThat(mySideModel.getHiddenSliders()).isEmpty();
    assertThat(mySideModel.getFloatingTools()).containsExactly(myFloatingToolWindow1, myFloatingToolWindow2).inOrder();
    verify(myListener).modelChanged(eq(mySideModel), eq(SWAP));
  }

  private void initProperties(@NotNull AttachedToolWindow... toolWindows) {
    int number = 0;
    for (AttachedToolWindow toolWindow : toolWindows) {
      String id = PROPERTY_PREFIX + ++number;
      when(toolWindow.getToolName()).thenReturn(id);
      doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          Object[] arguments = invocation.getArguments();
          PropertyType propertyType = (PropertyType)arguments[0];
          Boolean value = (Boolean)arguments[1];
          myProperties.setProperty(id + "." + propertyType.name(), value.toString());
          return null;
        }
      }).when(toolWindow).setProperty(any(PropertyType.class), anyBoolean());
      doAnswer(new Answer<Boolean>() {
        @Override
        public Boolean answer(InvocationOnMock invocation) throws Throwable {
          Object[] arguments = invocation.getArguments();
          PropertyType propertyType = (PropertyType)arguments[0];
          return Boolean.parseBoolean(myProperties.getProperty(id + "." + propertyType.name(), "false"));
        }
      }).when(toolWindow).getProperty(any(PropertyType.class));
      doCallRealMethod().when(toolWindow).isMinimized();
      doCallRealMethod().when(toolWindow).isLeft();
      doCallRealMethod().when(toolWindow).isFloating();
      doCallRealMethod().when(toolWindow).isSplit();
      doCallRealMethod().when(toolWindow).isAutoHide();
      doCallRealMethod().when(toolWindow).setMinimized(anyBoolean());
      doCallRealMethod().when(toolWindow).setLeft(anyBoolean());
      doCallRealMethod().when(toolWindow).setSplit(anyBoolean());
      doCallRealMethod().when(toolWindow).setFloating(anyBoolean());
      doCallRealMethod().when(toolWindow).setAutoHide(anyBoolean());
    }
  }
}
