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
package com.android.tools.idea.assistant.view;

import static com.android.tools.idea.concurrency.AsyncTestUtils.waitForCondition;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.assistant.AssistActionStateManager;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.assistant.datamodel.DefaultActionState;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.RunsInEdt;
import java.awt.event.ActionListener;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class StatefulButtonTest {
  private ActionListener myListener;
  private ActionData myAction;
  private AssistActionStateManager myStateManager;

  @Rule
  public ProjectRule projectRule = new ProjectRule();

  @Rule
  public EdtRule edtRule = new EdtRule();

  @Before
  public void setUp() {
    myListener = mock(ActionListener.class);
    myAction = mock(ActionData.class);
    when(myAction.getLabel()).thenReturn("test");
    when(myAction.getSuccessMessage()).thenReturn(null);

    myStateManager = mock(AssistActionStateManager.class);
    when(myStateManager.getStateDisplay(projectRule.getProject(), myAction, null))
      .thenReturn(new StatefulButtonMessage("fake", DefaultActionState.COMPLETE));
  }

  @Test
  public void testDefaultNoStateManager() throws TimeoutException {
    StatefulButton button = new StatefulButton(myAction, myListener, null, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertTrue(button.myButton.isEnabled());
    assertNull(button.myMessage);
  }

  @Test
  public void testNotApplicable() throws TimeoutException {
    when(myStateManager.getState(projectRule.getProject(), myAction)).thenReturn(DefaultActionState.NOT_APPLICABLE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertTrue(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  @Test
  public void testPartial() throws TimeoutException {
    when(myStateManager.getState(projectRule.getProject(), myAction)).thenReturn(DefaultActionState.PARTIALLY_COMPLETE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertTrue(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  @Test
  public void testError() throws TimeoutException {
    when(myStateManager.getState(projectRule.getProject(), myAction)).thenReturn(DefaultActionState.ERROR);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertFalse(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  @Test
  public void testComplete() throws TimeoutException {
    when(myStateManager.getState(projectRule.getProject(), myAction)).thenReturn(DefaultActionState.COMPLETE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertFalse(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  @Test
  public void testInProgress() throws TimeoutException {
    when(myStateManager.getState(projectRule.getProject(), myAction)).thenReturn(DefaultActionState.IN_PROGRESS);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertTrue(button.myButton.isVisible());
    assertFalse(button.myButton.isEnabled());
    assertTrue(button.myMessage.isVisible());
  }

  @Test
  public void testIncomplete() throws TimeoutException {
    when(myStateManager.getState(projectRule.getProject(), myAction)).thenReturn(DefaultActionState.INCOMPLETE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, projectRule.getProject());
    waitForCondition(5, TimeUnit.SECONDS, button::isLoaded);
    assertTrue(button.myButton.isVisible());
    assertFalse(button.myMessage.isVisible());
  }
}
