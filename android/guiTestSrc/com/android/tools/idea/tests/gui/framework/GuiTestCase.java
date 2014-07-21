/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.junit.After;
import org.junit.Before;

import javax.swing.*;

import static junit.framework.Assert.assertNotNull;

public abstract class GuiTestCase {
  private IdeTestApplication myApplication;

  protected Robot myRobot;

  @Before
  public void setUp() {
    myApplication = GuiActionRunner.execute(new GuiQuery<IdeTestApplication>() {
      @Override
      protected IdeTestApplication executeInEDT() throws Throwable {
        return IdeTestApplication.getInstance();
      }
    });

    assertNotNull(ApplicationManager.getApplication());

    // Wait till the IDE frame is up.
    Pause.pause(new Condition("'Find IDE Frame'") {
      @Override
      public boolean test() {
        JFrame visibleFrame = WindowManager.getInstance().findVisibleFrame();
        return visibleFrame != null;
      }
    });

    myRobot = BasicRobot.robotWithNewAwtHierarchy();
  }

  @After
  public void tearDown() {
    if (myApplication != null) {
      Disposer.dispose(myApplication);
    }

    if (myRobot != null) {
      myRobot.cleanUpWithoutDisposingWindows();
    }
  }
}
