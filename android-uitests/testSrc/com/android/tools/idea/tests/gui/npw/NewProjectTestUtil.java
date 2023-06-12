/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.npw.CppStandardType;
import com.android.tools.idea.tests.util.WizardUtils;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;

public class NewProjectTestUtil {

  public static boolean createNewProject(GuiTestRule guiTest, FormFactor tabName, String templateName) {
    System.out.println("\nValidating template: " + templateName+ " in: " +tabName.toString());

    WizardUtils.createNewProject(guiTest,tabName,templateName);
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    guiTest.ideFrame().clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    return (guiTest.ideFrame().invokeProjectMake(Wait.seconds(180)).isBuildSuccessful());
  }

  public static boolean createCppProject(GuiTestRule guiTest, FormFactor tabName, String templateName) {
    System.out.println("\nValidating template: " + templateName+ " in: " +tabName.toString());

    WizardUtils.createCppProject(guiTest,tabName,templateName);
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    guiTest.waitForBackgroundTasks();
    guiTest.ideFrame().clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    return (guiTest.ideFrame().invokeProjectMake(Wait.seconds(180)).isBuildSuccessful());
  }
}
