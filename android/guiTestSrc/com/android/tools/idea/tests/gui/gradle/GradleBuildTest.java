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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessageDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectSdkDialogFixture;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

public class GradleBuildTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testBuildWithInvalidJavaHome() throws IOException {
    String jdkPathPropertyName = "jdk.path";

    String jdkPathValue = System.getProperty(jdkPathPropertyName);
    if (isEmpty(jdkPathValue)) {
      String msg = String.format("Test '%1$s' skipped. It requires the system property '%2$s'.", getTestName(), jdkPathPropertyName);
      System.out.println(msg);
      return;
    }

    File jdkPath = new File(jdkPathValue);

    IdeFrameFixture projectFrame = openSimpleApplication();
    projectFrame.invokeProjectMakeAndSimulateFailure("Supplied javaHome is not a valid folder.");

    // Find message dialog explaining the source of the error.
    MessageDialogFixture.findByTitle(myRobot, "Gradle Running").clickOk();

    // Find the dialog to select the path of the JDK.
    SelectSdkDialogFixture selectSdkDialog = SelectSdkDialogFixture.find(myRobot);
    selectSdkDialog.setJdkPath(jdkPath)
                   .clickOk();

    File actualJdkPath = DefaultSdks.getDefaultJavaHome();
    assertNotNull(actualJdkPath);
    assertEquals(jdkPath.getPath(), actualJdkPath.getPath());
  }
}
