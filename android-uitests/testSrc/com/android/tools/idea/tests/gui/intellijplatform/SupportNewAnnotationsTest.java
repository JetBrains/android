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
package com.android.tools.idea.tests.gui.intellijplatform;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProblemsPaneFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class SupportNewAnnotationsTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private static final String LOCATION_MANAGER_CODE = "\n((getSystemService(Context.LOCATION_SERVICE) as LocationManager).getLastKnownLocation(\"gps\")";
  private  static final String IMPORT_STATEMENTS = "\n" +
                                                  "import android.content.Context\n" +
                                                  "import android.location.LocationManager\n" +
                                                  "\n";

  /**
   * Support for new Annotations (Permissions)
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1de55670-c5a5-43d7-a340-563bad5c6e83
   * <p>
   * <pre>
   *   Test Steps:
   *    1. Open Android Studio
   *    2. Create a new project or open any existing project.
   *    3. Open the MainActivity.java file.
   *    4. On any of the methods, say OnCreate, add the following line to the code: (Verify 1)
   *          ((LocationManager)getSystemService(Context.LOCATION_SERVICE)).getLastKnownLocation("gps");
   *    5. "Alt + Enter" on the keyboard invokes the Quickfix (the red light bulb) and that tapping on it gives option to add the permission.
   *    6. Select Add Permission ACCESS_FINE_LOCATION from Quickfix.
   *    7. Select Add Permission ACCESS_COARSE_LOCATION from Quickfix.
   *    8. "Alt + Enter" on the keyboard invokes the Quickfix (the red light bulb) and that tapping on it gives option to add permission check. (Verify 2)
   *
   *   Verify:
   *    1. Verify that there is a red underline on the added line of code and hovering over it displays the message that Check Permission needs to be added.
   *    2. After adding the permission, the following lines of code are added to the method for checking permission and the red underline disappears.
   *        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
   *          {
   *             // Consider calling
   *             //    public void requestPermissions(@NonNull String[] permissions, int requestCode)
   *             // here to request the missing permissions, and then overriding
   *             //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
   *             //                                          int[] grantResults)
   *             // to handle the case where the user grants the permission. See the documentation
   *             // for Activity#requestPermissions for more details.
   *             return;
   *          }
   *   </pre>
   * <p>
   */
  @Test
  public void testPermissions() {
    WizardUtils.createNewProject(guiTest,
                                 "Empty Views Activity",
                                 Language.Kotlin,
                                 BuildConfigurationLanguageForNewProject.KTS);

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editorFixture = ideFrame.getEditor();

    editorFixture.open("/app/src/main/java/com/google/myapplication/MainActivity.kt")
      .moveBetween("", "import android")
      .enterText(IMPORT_STATEMENTS)
      .moveBetween("activity_main)", "")
      .enterText(LOCATION_MANAGER_CODE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Wait.seconds(60)
        .expecting("To wait for error analysis to finish")
        .until(() ->
          editorFixture.getHighlights(HighlightSeverity.ERROR).size() > 0
        );

    GuiTests.waitForProjectIndexingToFinish(ideFrame.getProject());

    editorFixture.moveBetween("getSystem", "Service");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // To reduce test flakiness, invoking the shortcuts and closing it in the first attempt.
    editorFixture.invokeAction(EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    editorFixture.invokeAction(EditorFixture.EditorAction.ESCAPE);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Wait.seconds(5)
      .expecting("Wait needed to reduce flakiness.");

    editorFixture.moveBetween("getSystem", "Service");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editorFixture.invokeQuickfixAction("Add Permission ACCESS_FINE_LOCATION");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Wait.seconds(5)
      .expecting("Wait needed to reduce flakiness.");

    editorFixture.moveBetween("getSys", "temService");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editorFixture.invokeQuickfixAction("Add permission check");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String fileContents = editorFixture.getCurrentFileContents();
    assertThat(fileContents).contains("ActivityCompat#requestPermissions");
    assertThat(fileContents).contains("ActivityCompat.checkSelfPermission");
  }
}

