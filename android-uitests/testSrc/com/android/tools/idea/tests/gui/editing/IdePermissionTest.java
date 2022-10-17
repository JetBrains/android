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
package com.android.tools.idea.tests.gui.editing;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class IdePermissionTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verify IDE adds in location permissions and location permission checks
   *
   * <p>TT ID: 1de55670-c5a5-43d7-a340-563bad5c6e83
   *
   * <pre>
   *   Test steps:
   *   1. Import SimpleApplication project.
   *   2. Open MyActivity.java
   *   3. Add in a method call to the location manager.
   *   4. Use the quickfix popup list to automatically add location permissions
   *      and location permissions checks to the project.
   *   Verify:
   *   1. Code to check for location permissions should be found in MyActivity.java
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void ideAddsPermissionChecks() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication")
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .moveBetween("setContentView(R.layout.activity_my);\n    ", "}")
      .enterText(
        "((android.location.LocationManager)getSystemService("
        + "android.content.Context.LOCATION_SERVICE"
        + ")).getLastKnownLocation(\"test\");\n");

    // Initially, it takes some time to wait for the permission options showing.
    EditorFixture editor = guiTest.ideFrame().getEditor();
    Wait.seconds(10).expecting("Permission options to be showing.").until(() -> {
      try {
        editor.moveBetween("getSystemService", "")
          .invokeQuickfixActionWithoutBulb("Add Permission ACCESS_COARSE_LOCATION");
        return true;
      } catch (AssertionError e) {
        return false;
      }
    });

    Wait.seconds(10).expecting("Permission options to be showing.").until(() -> {
      try {
        editor.moveBetween("getSystemService", "")
          .invokeQuickfixActionWithoutBulb("Add Permission ACCESS_FINE_LOCATION");
        return true;
      } catch (AssertionError e) {
        return false;
      }
    });

    editor.moveBetween("getSystemService", "")
      .invokeQuickfixActionWithoutBulb("Add permission check");


    assertThat(editor.getCurrentFileContents())
      .contains("checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)");
    assertThat(editor.getCurrentFileContents())
      .contains("checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)");
  }
}
