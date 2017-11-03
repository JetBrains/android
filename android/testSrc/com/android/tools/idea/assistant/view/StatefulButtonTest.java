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

import com.android.tools.idea.assistant.AssistActionStateManager;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.android.tools.idea.assistant.datamodel.DefaultActionState;
import org.jetbrains.android.AndroidTestCase;

import java.awt.event.ActionListener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatefulButtonTest extends AndroidTestCase {
  private ActionListener myListener;
  private ActionData myAction;
  private AssistActionStateManager myStateManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myListener = mock(ActionListener.class);
    myAction = mock(ActionData.class);
    when(myAction.getLabel()).thenReturn("test");
    when(myAction.getSuccessMessage()).thenReturn(null);

    myStateManager = mock(AssistActionStateManager.class);
    when(myStateManager.getStateDisplay(getProject(), myAction, null))
      .thenReturn(new StatefulButtonMessage("fake", DefaultActionState.COMPLETE));
  }

  public void testDefaultNoStateManager() {
    StatefulButton button = new StatefulButton(myAction, myListener, null, getProject());
    assertTrue(button.myButton.isEnabled());
    assertTrue(button.myMessage == null);
  }

  public void testNotApplicable() {
    when(myStateManager.getState(getProject(), myAction)).thenReturn(DefaultActionState.NOT_APPLICABLE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, getProject());
    assertTrue(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  public void testPartial() {
    when(myStateManager.getState(getProject(), myAction)).thenReturn(DefaultActionState.PARTIALLY_COMPLETE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, getProject());
    assertTrue(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  public void testError() {
    when(myStateManager.getState(getProject(), myAction)).thenReturn(DefaultActionState.ERROR);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, getProject());
    assertFalse(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  public void testComplete() {
    when(myStateManager.getState(getProject(), myAction)).thenReturn(DefaultActionState.COMPLETE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, getProject());
    assertFalse(button.myButton.isVisible());
    assertTrue(button.myMessage.isVisible());
  }

  public void testInProgress() {
    when(myStateManager.getState(getProject(), myAction)).thenReturn(DefaultActionState.IN_PROGRESS);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, getProject());
    assertTrue(button.myButton.isVisible());
    assertFalse(button.myButton.isEnabled());
    assertTrue(button.myMessage.isVisible());
  }

  public void testIncomplete() {
    when(myStateManager.getState(getProject(), myAction)).thenReturn(DefaultActionState.INCOMPLETE);
    StatefulButton button = new StatefulButton(myAction, myListener, myStateManager, getProject());
    assertTrue(button.myButton.isVisible());
    assertFalse(button.myMessage.isVisible());
  }
}
