/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import org.fest.swing.core.Robot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertNull;

public class GuiTestRunnerTest extends GuiTestCase {
  Robot originalRobot;

  @Before
  public void setRobotToNull() {
    originalRobot = myRobot;
    myRobot = null;
  }

  @Test @IdeGuiTest
  public void beforeMethodsOrder() {
    // If setRobotToNull was executed after @Before methods from GuiTestCase, this should pass.
    assertNull(myRobot);
  }

  @After
  public void restoreRobot() {
    myRobot = originalRobot;
  }
}
