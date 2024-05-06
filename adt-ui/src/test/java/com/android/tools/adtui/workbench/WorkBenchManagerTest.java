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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.adtui.swing.FakeKeyboardFocusManager;
import com.android.tools.adtui.workbench.AttachedToolWindow.AttachedToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.ProjectRule;
import java.awt.Component;
import javax.swing.JPanel;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import sun.awt.AWTAccessor;

@RunWith(JUnit4.class)
public class WorkBenchManagerTest {
  private boolean initialUI = false;
  private WorkBench<?> myWorkBench1, myWorkBench2, myWorkBench3, myWorkBench4, myWorkBench5;
  private WorkBenchManager myManager;
  private FakeKeyboardFocusManager myFocusManager;
  private final ProjectRule projectRule = new ProjectRule();
  private final DisposableRule disposableRule = new DisposableRule();

  @Rule
  public RuleChain chain = RuleChain.outerRule(projectRule).around(disposableRule);

  @Before
  public void before() {
    initialUI = ExperimentalUI.isNewUI();
    Registry.get("ide.experimental.ui").setValue(true);
    myFocusManager = new FakeKeyboardFocusManager(disposableRule.getDisposable());
    myManager = new WorkBenchManager();
    Disposer.register(disposableRule.getDisposable(), myManager);
    myWorkBench1 = createWorkBench("A");
    myWorkBench2 = createWorkBench("A");
    myWorkBench3 = createWorkBench("A");
    myWorkBench4 = createWorkBench("B");
    myWorkBench5 = createWorkBench("B");
  }

  @After
  public void after() {
    Registry.get("ide.experimental.ui").setValue(initialUI);
  }

  @Test
  public void testUpdateOtherWorkBenches() {
    myManager.updateOtherWorkBenches(myWorkBench2);
    verify(myWorkBench1).updateModel();
    verify(myWorkBench2, never()).updateModel();
    verify(myWorkBench3).updateModel();
    verify(myWorkBench4, never()).updateModel();
    verify(myWorkBench5, never()).updateModel();
  }

  @Test
  public void testUpdateOtherWorkBenchesAfterUnregister() {
    myManager.unregister(myWorkBench3);
    myManager.updateOtherWorkBenches(myWorkBench2);
    verify(myWorkBench1).updateModel();
    verify(myWorkBench2, never()).updateModel();
    verify(myWorkBench3, never()).updateModel();
    verify(myWorkBench4, never()).updateModel();
    verify(myWorkBench5, never()).updateModel();
  }

  @Test
  public void testStoreDefaultLayout() {
    myManager.storeDefaultLayout();
    verify(myWorkBench1).storeDefaultLayout();
    verify(myWorkBench4).storeDefaultLayout();
  }

  @Test
  public void testRestoreDefaultLayout() {
    myManager.restoreDefaultLayout();
    verify(myWorkBench1).restoreDefaultLayout();
    verify(myWorkBench2).updateModel();
    verify(myWorkBench3).updateModel();
    verify(myWorkBench4).restoreDefaultLayout();
    verify(myWorkBench5).updateModel();
  }

  @Test
  public void testToolWindowIsActivated() {
    Pair<AttachedToolWindow<?>, Component> toolWindowPair1 = createToolWindowUnder(myWorkBench1);

    myFocusManager.setFocusOwner(toolWindowPair1.second);
    verify(toolWindowPair1.first).setActive(true);
    assertThat(myManager.getActiveToolWindow()).isEqualTo(toolWindowPair1.first);
  }

  @Test
  public void testOneToolWindowIsDeactivatedWhenAnotherIsActivated() {
    Pair<AttachedToolWindow<?>, Component> toolWindowPair1 = createToolWindowUnder(myWorkBench1);
    Pair<AttachedToolWindow<?>, Component> toolWindowPair2 = createToolWindowUnder(myWorkBench2);

    myFocusManager.setFocusOwner(toolWindowPair1.second);
    myFocusManager.setFocusOwner(toolWindowPair2.second);
    verify(toolWindowPair1.first).setActive(false);
    verify(toolWindowPair2.first).setActive(true);
    assertThat(myManager.getActiveToolWindow()).isEqualTo(toolWindowPair2.first);
  }

  @Test
  public void testToolWindowIsDeactivatedWhenAnUnrelatedComponentGainsFocus() {
    Pair<AttachedToolWindow<?>, Component> toolWindowPair1 = createToolWindowUnder(myWorkBench1);
    Component unrelatedComponent = new JPanel();

    myFocusManager.setFocusOwner(toolWindowPair1.second);
    myFocusManager.setFocusOwner(unrelatedComponent);
    verify(toolWindowPair1.first).setActive(false);
    assertThat(myManager.getActiveToolWindow()).isNull();
  }

  @Test
  public void testToolWindowIsDeactivatedWhenWorkBenchIsClosed() {
    Pair<AttachedToolWindow<?>, Component> toolWindowPair1 = createToolWindowUnder(myWorkBench1);

    myFocusManager.setFocusOwner(toolWindowPair1.second);
    myManager.unregister(myWorkBench1);
    verify(toolWindowPair1.first).setActive(false);
    assertThat(myManager.getActiveToolWindow()).isNull();
  }

  private WorkBench<?> createWorkBench(@NotNull String name) {
    WorkBench<?> workBench = mock(WorkBench.class);
    when(workBench.getName()).thenReturn(name);
    myManager.register(workBench);
    return workBench;
  }

  private Pair<AttachedToolWindow<?>, Component> createToolWindowUnder(@NotNull WorkBench<?> workbench) {
    Component component = new JPanel();
    AttachedToolWindow<?> toolWindow = mock(AttachedToolWindow.class);
    AttachedToolWindowPanel panel = new AttachedToolWindowPanel(toolWindow);
    panel.add(component);
    AWTAccessor.getComponentAccessor().setParent(panel, workbench);
    return Pair.pair(toolWindow, component);
  }
}
