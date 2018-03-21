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


import com.android.tools.idea.assistant.datamodel.DefaultActionState;
import com.intellij.icons.AllIcons;
import junit.framework.TestCase;

public class StatefulButtonMessageTest extends TestCase {

  public void testNotApplicable() {
    StatefulButtonMessage message = new StatefulButtonMessage("test", DefaultActionState.NOT_APPLICABLE);
    assertNull(message.myMessageDisplay);
  }

  public void testInProgress() {
    StatefulButtonMessage message = new StatefulButtonMessage("test", DefaultActionState.IN_PROGRESS);
    assertNull(message.myMessageDisplay);
  }

  public void testComplete() {
    StatefulButtonMessage message = new StatefulButtonMessage("test", DefaultActionState.COMPLETE);
    assertEquals(message.myMessageDisplay.getIcon(), AllIcons.RunConfigurations.TestPassed);
    assertEquals(message.myMessageDisplay.getForeground(), UIUtils.getSuccessColor());
  }

  public void testPartiallyComplete() {
    StatefulButtonMessage message = new StatefulButtonMessage("test", DefaultActionState.PARTIALLY_COMPLETE);
    assertEquals(message.myMessageDisplay.getIcon(), AllIcons.RunConfigurations.TestPassed);
    assertEquals(message.myMessageDisplay.getForeground(), UIUtils.getSuccessColor());
  }

  public void testError() {
    StatefulButtonMessage message = new StatefulButtonMessage("test", DefaultActionState.ERROR);
    assertEquals(message.myMessageDisplay.getIcon(), AllIcons.RunConfigurations.TestFailed);
    assertEquals(message.myMessageDisplay.getForeground(), UIUtils.getFailureColor());
  }
}
